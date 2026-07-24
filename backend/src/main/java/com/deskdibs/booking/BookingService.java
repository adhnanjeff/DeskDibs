package com.deskdibs.booking;

import com.deskdibs.common.OfficeClock;
import com.deskdibs.common.OfficeProperties;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatReservation;
import com.deskdibs.seat.SeatReservationRepository;
import com.deskdibs.team.TeamMemberRepository;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The booking engine: claim, cancel, move, check in.
 *
 * <h2>The database settles every race</h2>
 * A claim validates the rules it can — the date window, the seat's own status, a team hold — and
 * then simply inserts. There is no {@code SELECT ... FOR UPDATE}, no application lock, no retry
 * loop, and no "is this seat free?" read used as the guard. Freeness is decided by
 * {@code uq_seat_active_per_date} and {@code uq_user_active_per_date} at insert time, below the
 * application, where no bug in this class can bypass it. The only work left here is translating a
 * lost race into an exception that says something useful.
 *
 * <h2>Why the transaction is explicit</h2>
 * The claim itself runs in one transaction, as the design requires. The <em>translation</em>
 * deliberately runs after that transaction has rolled back, which is why this class drives a
 * {@link TransactionTemplate} instead of wearing {@code @Transactional} on {@code claim}:
 * <ul>
 *   <li>a PostgreSQL transaction is aborted by the constraint violation, so it cannot answer
 *       "who won?" — every subsequent statement on that connection fails;</li>
 *   <li>doing the lookup in a {@code REQUIRES_NEW} transaction instead would make a losing thread
 *       hold two connections at once. Under the 150-thread race against a 32-connection pool that
 *       is a textbook pool deadlock.</li>
 * </ul>
 * Letting the failed transaction end first costs one extra short read on the failure path only.
 */
@Service
public class BookingService {

    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final AppUserRepository users;
    private final SeatReservationRepository reservations;
    private final TeamMemberRepository teamMembers;
    private final OfficeClock officeClock;
    private final OfficeProperties office;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher events;

    public BookingService(BookingRepository bookings,
                          SeatRepository seats,
                          AppUserRepository users,
                          SeatReservationRepository reservations,
                          TeamMemberRepository teamMembers,
                          OfficeClock officeClock,
                          OfficeProperties office,
                          TransactionTemplate transactionTemplate,
                          ApplicationEventPublisher events) {
        this.bookings = bookings;
        this.seats = seats;
        this.users = users;
        this.reservations = reservations;
        this.teamMembers = teamMembers;
        this.officeClock = officeClock;
        this.office = office;
        this.transactionTemplate = transactionTemplate;
        this.events = events;
    }

    // ─── Claim ───────────────────────────────────────────────────────────────────

    /**
     * Claim {@code seatId} for {@code userId} on {@code date}.
     *
     * <p>One transaction, in this order: date window, seat status, team hold, insert. A repeated
     * call with the same {@code idempotencyKey} returns the original booking instead of failing.
     *
     * @param idempotencyKey client-generated key, or {@code null}. Blank is treated as absent.
     * @throws DateOutsideBookingWindowException the date is in the past or beyond the horizon
     * @throws SeatNotBookableException          the seat is DISABLED or BROKEN
     * @throws SeatReservedForTeamException      a team hold still covers the seat and the caller
     *                                           is not in that team
     * @throws SeatAlreadyBookedException        somebody else won the seat; names the winner
     * @throws AlreadyBookedThatDayException     the caller already holds a seat that day; names it
     * @throws IdempotencyKeyConflictException   the key was first used for a different claim
     */
    public BookingView claim(long userId, long seatId, LocalDate date, String idempotencyKey) {
        ClaimRequest request = new ClaimRequest(userId, seatId, date, normaliseKey(idempotencyKey));
        return attemptWrite(() -> insertClaim(request), request);
    }

    private BookingView insertClaim(ClaimRequest request) {
        BookingView replay = replayOf(request);
        if (replay != null) {
            return replay;
        }

        requireDateWithinWindow(request.date());
        Seat seat = requireBookableSeat(request.seatId());
        AppUser user = requireUser(request.userId());
        requireNotHeldForAnotherTeam(seat, request.date(), user);

        // The insert is the authority. If it loses, it loses at the database.
        BookingView claimed = BookingView.of(bookings.saveAndFlush(
                new Booking(seat, user, request.date(), request.idempotencyKey())));
        publishSeatChanged(claimed.seatId(), claimed.bookingDate());
        return claimed;
    }

    // ─── Move ────────────────────────────────────────────────────────────────────

    /**
     * Move {@code userId} to {@code toSeatId} on {@code date}: cancel whatever they hold that day
     * and claim the new seat, atomically.
     *
     * <p>Both halves are one transaction, so a target seat that cannot be claimed leaves the
     * original booking untouched and ACTIVE — losing the new seat must never also lose the old
     * one. The cancel is flushed before the insert, so the freed row leaves
     * {@code uq_user_active_per_date} before the new row tries to enter it.
     *
     * <p>Moving to the seat you already hold is a no-op that returns the existing booking. Moving
     * when you hold nothing that day is simply a claim.
     */
    public BookingView move(long userId, long toSeatId, LocalDate date, String idempotencyKey) {
        ClaimRequest request = new ClaimRequest(userId, toSeatId, date, normaliseKey(idempotencyKey));
        return attemptWrite(() -> insertMove(request), request);
    }

    private BookingView insertMove(ClaimRequest request) {
        BookingView replay = replayOf(request);
        if (replay != null) {
            return replay;
        }

        requireDateWithinWindow(request.date());
        Seat seat = requireBookableSeat(request.seatId());
        AppUser user = requireUser(request.userId());
        requireNotHeldForAnotherTeam(seat, request.date(), user);

        Booking current = bookings
                .findByUserAndDateWithSeatAndUser(request.userId(), request.date(), BookingStatus.ACTIVE)
                .orElse(null);
        if (current != null) {
            if (current.getSeat().getId() == request.seatId()) {
                return BookingView.of(current);
            }
            long previousSeatId = current.getSeat().getId();
            current.setStatus(BookingStatus.CANCELLED);
            bookings.saveAndFlush(current);
            publishSeatChanged(previousSeatId, request.date());
        }

        BookingView moved = BookingView.of(bookings.saveAndFlush(
                new Booking(seat, user, request.date(), request.idempotencyKey())));
        publishSeatChanged(moved.seatId(), moved.bookingDate());
        return moved;
    }

    // ─── Cancel ──────────────────────────────────────────────────────────────────

    /**
     * Cancel a booking on behalf of {@code actingUserId}.
     *
     * <p>Object-level authorization, server-side: the owner, the manager of a team the owner
     * belongs to, or an ADMIN. Anybody else is refused — including someone who merely knows the id.
     *
     * <p>Cancelling flips the row to CANCELLED, which drops it out of the partial unique indexes
     * and makes the seat immediately claimable by anyone. Nothing is deleted; the row stays as
     * history.
     */
    @Transactional
    public BookingView cancel(long bookingId, long actingUserId) {
        Booking booking = requireBooking(bookingId);
        AppUser actor = requireUser(actingUserId);
        requireMayAct(booking, actor, BookingAccessDeniedException.Action.CANCEL);

        if (!booking.isActive()) {
            throw new BookingNotActiveException(bookingId, booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        BookingView cancelled = BookingView.of(bookings.saveAndFlush(booking));
        publishSeatChanged(cancelled.seatId(), cancelled.bookingDate());
        return cancelled;
    }

    // ─── Check-in ────────────────────────────────────────────────────────────────

    /**
     * Record that the owner turned up. Only the owner may do this — a manager or an admin
     * vouching for somebody's attendance would defeat the no-show release entirely.
     *
     * <p>Only today's booking can be checked into, where "today" is the office clock's, and only
     * once.
     */
    @Transactional
    public BookingView checkIn(long bookingId, long actingUserId) {
        Booking booking = requireBooking(bookingId);
        AppUser actor = requireUser(actingUserId);

        if (booking.getUser().getId() != actor.getId().longValue()) {
            throw new BookingAccessDeniedException(bookingId, actingUserId, booking.getUser().getId(),
                    BookingAccessDeniedException.Action.CHECK_IN);
        }
        if (!booking.isActive()) {
            throw new BookingNotActiveException(bookingId, booking.getStatus());
        }

        LocalDate today = officeClock.today();
        if (!booking.getBookingDate().equals(today)) {
            throw new CheckInNotForTodayException(bookingId, booking.getBookingDate(), today);
        }
        if (booking.getCheckedInAt() != null) {
            throw new AlreadyCheckedInException(bookingId, booking.getCheckedInAt());
        }

        booking.setCheckedInAt(officeClock.timestamp());
        BookingView checkedIn = BookingView.of(bookings.saveAndFlush(booking));
        publishSeatChanged(checkedIn.seatId(), checkedIn.bookingDate());
        return checkedIn;
    }

    // ─── Reading ─────────────────────────────────────────────────────────────────

    /** The caller's own bookings with a date in {@code [from, to]}, earliest first. */
    @Transactional(readOnly = true)
    public List<BookingView> findMine(long userId, LocalDate from, LocalDate to) {
        return bookings.findByUserIdAndBookingDateBetweenOrderByBookingDateAscFetchSeatAndUser(userId, from, to)
                .stream()
                .map(BookingView::of)
                .toList();
    }

    // ─── The rules ───────────────────────────────────────────────────────────────

    /** Rule 1: within {@code [today, today + horizon]}, with "today" read from the office clock. */
    private void requireDateWithinWindow(LocalDate date) {
        LocalDate earliest = officeClock.today();
        LocalDate latest = earliest.plusDays(office.bookingHorizonDays());
        if (date.isBefore(earliest) || date.isAfter(latest)) {
            throw new DateOutsideBookingWindowException(date, earliest, latest);
        }
    }

    /** Rule 2: the seat itself has to be in the pool. */
    private Seat requireBookableSeat(long seatId) {
        Seat seat = seats.findById(seatId).orElseThrow(() -> new SeatNotFoundException(seatId));
        if (!seat.isBookable()) {
            throw new SeatNotBookableException(seatId, seat.getLabel(), seat.getStatus());
        }
        return seat;
    }

    /**
     * Rule 3: a team hold blocks outsiders until it releases.
     *
     * <p>Soft by design — there is no job and no state change, the check simply stops applying
     * once the office clock passes the hold's release time <em>on the booked date</em>. Being in
     * any one of the holding teams is enough.
     */
    private void requireNotHeldForAnotherTeam(Seat seat, LocalDate date, AppUser user) {
        List<SeatReservation> holds = reservations
                .findBySeatIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(seat.getId(), date, date);

        SeatReservation blocking = null;
        for (SeatReservation hold : holds) {
            if (!officeClock.isBefore(date, hold.getReleaseAtTime())) {
                continue; // released: no longer enforced
            }
            if (teamMembers.existsByIdTeamIdAndIdUserId(hold.getTeam().getId(), user.getId())) {
                return; // a member of a holding team is exactly who the hold is for
            }
            if (blocking == null) {
                blocking = hold;
            }
        }

        if (blocking != null) {
            throw new SeatReservedForTeamException(seat.getId(), seat.getLabel(), date,
                    blocking.getTeam().getId(), blocking.getTeam().getName(), blocking.getReleaseAtTime());
        }
    }

    /** Object-level authorization for an existing booking. */
    private void requireMayAct(Booking booking, AppUser actor, BookingAccessDeniedException.Action action) {
        long ownerId = booking.getUser().getId();
        boolean permitted = ownerId == actor.getId().longValue()
                || actor.getRole() == UserRole.ADMIN
                // The stored manager-of-their-team relationship, not a role claim from a client.
                || teamMembers.existsMembershipManagedBy(ownerId, actor.getId());

        if (!permitted) {
            throw new BookingAccessDeniedException(booking.getId(), actor.getId(), ownerId, action);
        }
    }

    // ─── Idempotency ─────────────────────────────────────────────────────────────

    /**
     * The original result of a key that has been seen before, or {@code null} if it has not.
     *
     * <p>A replay must be the <em>same</em> claim. Same key with a different seat, date or user is
     * a client bug, and handing back a booking for a seat the caller did not ask for would be the
     * worst possible answer to it.
     */
    private BookingView replayOf(ClaimRequest request) {
        if (request.idempotencyKey() == null) {
            return null;
        }
        return bookings.findByIdempotencyKeyWithSeatAndUser(request.idempotencyKey())
                .map(original -> replayOrReject(original, request))
                .orElse(null);
    }

    private BookingView replayOrReject(Booking original, ClaimRequest request) {
        BookingView view = BookingView.of(original);
        boolean sameClaim = view.userId() == request.userId()
                && view.seatId() == request.seatId()
                && view.bookingDate().equals(request.date());
        if (!sameClaim) {
            throw new IdempotencyKeyConflictException(request.idempotencyKey(), view,
                    request.userId(), request.seatId(), request.date());
        }
        return view;
    }

    private static String normaliseKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ─── Translating a lost race ─────────────────────────────────────────────────

    /**
     * Runs the write in one transaction and, if the database refused it, turns the constraint that
     * fired into a domain exception that explains itself.
     *
     * <p>The {@code catch} body runs <em>after</em> the failed transaction has rolled back and
     * released its connection — see the class javadoc for why that matters.
     */
    private BookingView attemptWrite(Supplier<BookingView> write, ClaimRequest request) {
        try {
            return transactionTemplate.execute(status -> write.get());
        } catch (DataIntegrityViolationException violation) {
            return translate(violation, request);
        }
    }

    private BookingView translate(DataIntegrityViolationException violation, ClaimRequest request) {
        return switch (constraintBehind(violation)) {
            case SEAT_TAKEN -> throw seatAlreadyBooked(violation, request);
            case USER_ALREADY_BOOKED -> throw alreadyBookedThatDay(violation, request);
            case IDEMPOTENCY_KEY -> replayAfterLosingTheKeyRace(violation, request);
            // Not one of ours: a foreign key, a check constraint, a not-null. Dressing it up as a
            // booking rule would hide a real defect, so it goes out as it came in.
            case OTHER -> throw violation;
        };
    }

    /** Names whoever actually holds the seat now, read after the losing insert failed. */
    private SeatAlreadyBookedException seatAlreadyBooked(DataIntegrityViolationException violation,
                                                         ClaimRequest request) {
        Booking winner = bookings
                .findBySeatAndDateWithSeatAndUser(request.seatId(), request.date(), BookingStatus.ACTIVE)
                .orElse(null);
        String seatLabel = winner != null
                ? winner.getSeat().getLabel()
                : seats.findById(request.seatId()).map(Seat::getLabel).orElse(null);

        return new SeatAlreadyBookedException(request.seatId(), seatLabel, request.date(),
                winner == null ? null : winner.getUser().getId(),
                winner == null ? null : winner.getUser().getDisplayName(),
                violation);
    }

    /** Names the seat the caller already holds that day, so the UI can offer the move. */
    private AlreadyBookedThatDayException alreadyBookedThatDay(DataIntegrityViolationException violation,
                                                               ClaimRequest request) {
        Booking existing = bookings
                .findByUserAndDateWithSeatAndUser(request.userId(), request.date(), BookingStatus.ACTIVE)
                .orElse(null);

        return new AlreadyBookedThatDayException(request.userId(), request.date(),
                existing == null ? null : existing.getId(),
                existing == null ? null : existing.getSeat().getId(),
                existing == null ? null : existing.getSeat().getLabel(),
                violation);
    }

    /**
     * Two identical requests raced with the same key and this one lost the insert. The other one
     * is the original; return it, exactly as a sequential replay would have.
     */
    private BookingView replayAfterLosingTheKeyRace(DataIntegrityViolationException violation,
                                                    ClaimRequest request) {
        BookingView replay = replayOf(request);
        if (replay == null) {
            // The row that beat us is gone again. Nothing honest left to return.
            throw violation;
        }
        return replay;
    }

    private static ViolatedConstraint constraintBehind(DataIntegrityViolationException violation) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException hibernate) {
                ViolatedConstraint named = ViolatedConstraint.withIndexName(hibernate.getConstraintName());
                if (named != ViolatedConstraint.OTHER) {
                    return named;
                }
            }
            ViolatedConstraint mentioned = ViolatedConstraint.mentionedIn(cause.getMessage());
            if (mentioned != ViolatedConstraint.OTHER) {
                return mentioned;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return ViolatedConstraint.OTHER;
    }

    /**
     * The three partial unique indexes from {@code V1__core_schema.sql}, and which failure each one
     * means. The driver usually reports the index name outright; the message is scanned as a
     * fallback so a driver or dialect that only puts it in the text still translates correctly.
     */
    private enum ViolatedConstraint {

        SEAT_TAKEN("uq_seat_active_per_date"),
        USER_ALREADY_BOOKED("uq_user_active_per_date"),
        IDEMPOTENCY_KEY("uq_booking_idempotency"),
        OTHER(null);

        private final String indexName;

        ViolatedConstraint(String indexName) {
            this.indexName = indexName;
        }

        static ViolatedConstraint withIndexName(String name) {
            if (name == null) {
                return OTHER;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            for (ViolatedConstraint candidate : values()) {
                if (candidate.indexName != null && candidate.indexName.equals(lower)) {
                    return candidate;
                }
            }
            return OTHER;
        }

        static ViolatedConstraint mentionedIn(String message) {
            if (message == null) {
                return OTHER;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            for (ViolatedConstraint candidate : values()) {
                if (candidate.indexName != null && lower.contains(candidate.indexName)) {
                    return candidate;
                }
            }
            return OTHER;
        }
    }

    // ─── Real-time ───────────────────────────────────────────────────────────────

    /**
     * Signals that {@code seatId}'s availability on {@code bookingDate} changed. Picked up by
     * {@code com.deskdibs.realtime.SeatMapBroadcastListener}, whose
     * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} only fires once the transaction
     * this call is nested inside actually commits — never for a claim that goes on to lose the
     * race, because that path throws before reaching a call to this method at all.
     */
    private void publishSeatChanged(long seatId, LocalDate bookingDate) {
        events.publishEvent(new SeatAvailabilityChangedEvent(seatId, bookingDate));
    }

    // ─── Lookups ─────────────────────────────────────────────────────────────────

    private Booking requireBooking(long bookingId) {
        return bookings.findByIdWithSeatAndUser(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private AppUser requireUser(long userId) {
        return users.findById(userId).orElseThrow(() -> new BookingUserNotFoundException(userId));
    }

    /** One attempt to hold one seat: everything the rules and the error translation need. */
    private record ClaimRequest(long userId, long seatId, LocalDate date, String idempotencyKey) {
    }
}
