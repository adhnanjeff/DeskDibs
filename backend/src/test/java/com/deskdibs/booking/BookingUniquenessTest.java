package com.deskdibs.booking;

import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The core invariant, proved against the database rather than against application code:
 *
 * <blockquote>A seat has at most one ACTIVE booking on any given date. A person holds at most one
 * ACTIVE booking on any given date.</blockquote>
 *
 * <p>Each pair of tests checks both halves of a <em>partial</em> unique index: the constraint
 * rejects a second ACTIVE row, and — the part a plain unique index would get wrong — stops
 * applying the moment the first row leaves ACTIVE.
 */
class BookingUniquenessTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);
    private static final LocalDate OTHER_DATE = LocalDate.of(2026, 8, 4);

    private final BookingRepository bookingRepository;
    private final AppUserRepository appUserRepository;
    private final SeatRepository seatRepository;
    private final TransactionTemplate transactionTemplate;

    private Long seatId;
    private Long otherSeatId;
    private Long aliceId;
    private Long bobId;

    BookingUniquenessTest(BookingRepository bookingRepository,
                          AppUserRepository appUserRepository,
                          SeatRepository seatRepository,
                          TransactionTemplate transactionTemplate) {
        this.bookingRepository = bookingRepository;
        this.appUserRepository = appUserRepository;
        this.seatRepository = seatRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @BeforeEach
    void resetBookingsAndPeople() {
        bookingRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();

        seatId = seat("R3-A2");
        otherSeatId = seat("R3-A3");
        aliceId = person("alice@deskdibs.test", "Alice M.");
        bobId = person("bob@deskdibs.test", "Bob T.");
    }

    @Test
    @DisplayName("a second ACTIVE booking for the same seat on the same date is rejected by the database")
    void aSecondActiveBookingForTheSameSeatOnTheSameDateIsRejected() {
        claim(seatId, aliceId, DATE);

        assertThatThrownBy(() -> claim(seatId, bobId, DATE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_seat_active_per_date");

        assertThat(activeBookingsForSeat()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same seat is claimable again once the first booking is cancelled")
    void theSameSeatIsClaimableAgainOnceTheFirstBookingIsCancelled() {
        Long alicesBooking = claim(seatId, aliceId, DATE);
        cancel(alicesBooking);

        assertThatCode(() -> claim(seatId, bobId, DATE)).doesNotThrowAnyException();

        // Two rows now share (seat_id, booking_date). Only a *partial* index permits that;
        // a plain unique index on the pair would have rejected the second claim.
        assertThat(bookingRepository.findAll())
                .as("the cancelled row is kept as history, not deleted")
                .hasSize(2);
        assertThat(activeBookingsForSeat()).isEqualTo(1);
    }

    @Test
    @DisplayName("a person cannot hold two ACTIVE bookings on the same date")
    void aPersonCannotHoldTwoActiveBookingsOnTheSameDate() {
        claim(seatId, aliceId, DATE);

        assertThatThrownBy(() -> claim(otherSeatId, aliceId, DATE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_user_active_per_date");

        assertThat(bookingRepository.countByUserIdAndBookingDateAndStatus(aliceId, DATE, BookingStatus.ACTIVE))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a person can move to another seat once their first booking is cancelled")
    void aPersonCanMoveToAnotherSeatOnceTheirFirstBookingIsCancelled() {
        Long alicesBooking = claim(seatId, aliceId, DATE);
        cancel(alicesBooking);

        assertThatCode(() -> claim(otherSeatId, aliceId, DATE)).doesNotThrowAnyException();

        assertThat(bookingRepository.findAll()).hasSize(2);
        assertThat(bookingRepository.countByUserIdAndBookingDateAndStatus(aliceId, DATE, BookingStatus.ACTIVE))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("neither guard interferes with bookings on a different date")
    void neitherGuardInterferesWithBookingsOnADifferentDate() {
        claim(seatId, aliceId, DATE);

        assertThatCode(() -> claim(seatId, aliceId, OTHER_DATE)).doesNotThrowAnyException();

        assertThat(bookingRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("an idempotency key cannot be reused, so a retried claim can be recognised")
    void anIdempotencyKeyCannotBeReused() {
        String key = "claim-7f3c1a";
        transactionTemplate.executeWithoutResult(status -> bookingRepository.saveAndFlush(
                new Booking(seatRepository.getReferenceById(seatId),
                        appUserRepository.getReferenceById(aliceId), DATE, key)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                bookingRepository.saveAndFlush(
                        new Booking(seatRepository.getReferenceById(otherSeatId),
                                appUserRepository.getReferenceById(bobId), OTHER_DATE, key))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_booking_idempotency");

        assertThat(bookingRepository.findByIdempotencyKey(key)).isPresent();
    }

    @Test
    @DisplayName("bookings without an idempotency key do not collide with each other")
    void bookingsWithoutAnIdempotencyKeyDoNotCollideWithEachOther() {
        // The idempotency index is partial too: NULL keys are excluded entirely.
        claim(seatId, aliceId, DATE);

        assertThatCode(() -> claim(otherSeatId, bobId, DATE)).doesNotThrowAnyException();
    }

    private Long claim(Long seat, Long user, LocalDate date) {
        return transactionTemplate.execute(status -> bookingRepository.saveAndFlush(
                new Booking(seatRepository.getReferenceById(seat),
                        appUserRepository.getReferenceById(user), date, null)).getId());
    }

    private void cancel(Long bookingId) {
        transactionTemplate.executeWithoutResult(status -> {
            Booking booking = bookingRepository.findById(bookingId).orElseThrow();
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.saveAndFlush(booking);
        });
    }

    private long activeBookingsForSeat() {
        return bookingRepository.countBySeatIdAndBookingDateAndStatus(seatId, DATE, BookingStatus.ACTIVE);
    }

    private Long seat(String label) {
        return seatRepository.findByLabel(label).map(Seat::getId).orElseThrow();
    }

    private Long person(String email, String displayName) {
        AppUser user = new AppUser(email, displayName, UserRole.EMPLOYEE);
        return appUserRepository.saveAndFlush(user).getId();
    }
}
