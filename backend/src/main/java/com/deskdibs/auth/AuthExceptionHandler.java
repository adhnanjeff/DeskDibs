package com.deskdibs.auth;

import com.deskdibs.booking.AlreadyBookedThatDayException;
import com.deskdibs.booking.AlreadyCheckedInException;
import com.deskdibs.booking.BookingAccessDeniedException;
import com.deskdibs.booking.BookingErrorCode;
import com.deskdibs.booking.BookingException;
import com.deskdibs.booking.BookingNotActiveException;
import com.deskdibs.booking.BookingNotFoundException;
import com.deskdibs.booking.BookingUserNotFoundException;
import com.deskdibs.booking.CheckInNotForTodayException;
import com.deskdibs.booking.DateOutsideBookingWindowException;
import com.deskdibs.booking.IdempotencyKeyConflictException;
import com.deskdibs.booking.SeatAlreadyBookedException;
import com.deskdibs.booking.SeatNotBookableException;
import com.deskdibs.booking.SeatNotFoundException;
import com.deskdibs.booking.SeatReservedForTeamException;
import com.deskdibs.common.OfficeClock;
import com.deskdibs.team.InvalidReservationRangeException;
import com.deskdibs.team.ReservationAccessDeniedException;
import com.deskdibs.team.ReservationErrorCode;
import com.deskdibs.team.ReservationException;
import com.deskdibs.team.ReservationNotFoundException;
import com.deskdibs.team.ReservationSeatNotFoundException;
import com.deskdibs.team.TeamNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The application's single {@code @RestControllerAdvice}.
 *
 * <p>It handles the refusals that come out of a <em>handler</em> — a login that failed, a body
 * that would not validate, a booking or reservation rule the service layer refused. The refusals
 * decided before any handler runs (no token, bad token, wrong role) never reach here, because at
 * that point in the filter chain there is no controller to advise; those are written by
 * {@link JsonAuthenticationEntryPoint} and {@link JsonAccessDeniedHandler}. Every route emits the
 * same {@link AuthErrorResponse} shape, so the split is invisible to a client.
 *
 * <p>Phase 4 folds the booking and reservation exceptions into this class rather than adding a
 * second or third advice — {@code BookingErrorCode} and {@code ReservationErrorCode} already carry
 * the same stable-identity contract as {@link AuthErrorCode}, so all three merge into one wire
 * vocabulary without any of them changing.
 *
 * <p>Nothing here ever returns an exception's own {@code getMessage()}. Every subclass in the
 * booking and reservation hierarchies documents that field as log-only; the message sent to a
 * client is written fresh per error code below, and the fields those exceptions were built to carry
 * — a seat label, the winner's display name, an allowed date range — travel in {@code details}
 * instead of being parsed back out of a sentence. A stack trace, a class name, or a raw SQL
 * fragment reaching a client is a leak, and the way that happens is by someone writing
 * {@code e.getMessage()} into a response body once.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final String VALIDATION_MESSAGE = "The request body is not valid.";
    private static final String INVALID_PARAMETER_MESSAGE = "One or more request parameters are not valid.";
    private static final String ACCESS_DENIED_MESSAGE = "You do not have permission to perform this action.";

    private final OfficeClock officeClock;

    public AuthExceptionHandler(OfficeClock officeClock) {
        this.officeClock = officeClock;
    }

    // ─── Authentication / authorization (auth package) ───────────────────────────

    /**
     * Chiefly {@link InvalidCredentialsException} from a failed login — 401, with wording that
     * cannot be used to tell one account from another; see {@link LocalLoginService} for why all
     * four causes look identical. Also catches the ones that should be unreachable, such as
     * {@code CurrentUser.require()} on an endpoint that slipped through as public, so that even a
     * mistake fails closed with a 401 rather than a 500.
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<AuthErrorResponse> handleAuthFailure(AuthException refusal,
                                                               HttpServletRequest request) {
        HttpStatus status = refusal.errorCode() == AuthErrorCode.ACCESS_DENIED
                ? HttpStatus.FORBIDDEN
                : HttpStatus.UNAUTHORIZED;
        return respond(status, refusal.errorCode().name(), refusal.publicMessage(), null, request);
    }

    // ─── Booking domain ───────────────────────────────────────────────────────────

    /**
     * Every refusal {@code BookingService} raises, mapped by its stable {@code BookingErrorCode}
     * rather than by re-deriving a status per {@code catch} block, so the status a code maps to can
     * never drift between two call sites.
     */
    @ExceptionHandler(BookingException.class)
    public ResponseEntity<AuthErrorResponse> handleBookingFailure(BookingException refusal,
                                                                  HttpServletRequest request) {
        BookingErrorCode code = refusal.errorCode();
        return respond(bookingStatus(code), code.name(), bookingMessage(code), bookingDetails(refusal), request);
    }

    private static HttpStatus bookingStatus(BookingErrorCode code) {
        return switch (code) {
            case DATE_OUTSIDE_BOOKING_WINDOW -> HttpStatus.BAD_REQUEST;
            case SEAT_RESERVED_FOR_TEAM, BOOKING_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case BOOKING_NOT_FOUND, SEAT_NOT_FOUND, USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SEAT_ALREADY_BOOKED, ALREADY_BOOKED_THAT_DAY, SEAT_NOT_BOOKABLE, BOOKING_NOT_ACTIVE,
                 ALREADY_CHECKED_IN, CHECK_IN_NOT_FOR_TODAY, IDEMPOTENCY_KEY_CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    private static String bookingMessage(BookingErrorCode code) {
        return switch (code) {
            case DATE_OUTSIDE_BOOKING_WINDOW -> "The requested date is outside the allowed booking window.";
            case SEAT_NOT_BOOKABLE -> "This seat is not available for booking.";
            case SEAT_RESERVED_FOR_TEAM -> "This seat is reserved for a team until it releases.";
            case SEAT_ALREADY_BOOKED -> "This seat is already booked for that date.";
            case ALREADY_BOOKED_THAT_DAY -> "You already have a seat booked for that date.";
            case IDEMPOTENCY_KEY_CONFLICT -> "This idempotency key was already used for a different request.";
            case BOOKING_NOT_FOUND -> "No booking was found with that id.";
            case BOOKING_NOT_ACTIVE -> "This booking is no longer active.";
            case BOOKING_ACCESS_DENIED -> ACCESS_DENIED_MESSAGE;
            case CHECK_IN_NOT_FOR_TODAY -> "Check-in is only allowed on the day of the booking.";
            case ALREADY_CHECKED_IN -> "This booking has already been checked in.";
            case SEAT_NOT_FOUND -> "No seat was found with that id.";
            case USER_NOT_FOUND -> "No user was found with that id.";
        };
    }

    /**
     * The structured fields each exception was deliberately built with. {@code null} for
     * {@link BookingAccessDeniedException} on purpose: which booking, whose it is, and why the
     * actor was refused are facts about the system's shape, and hand them to somebody already
     * established as not entitled to the resource only helps them map it — the same reasoning
     * {@link JsonAccessDeniedHandler} already applies to every pre-controller 403.
     */
    private static Map<String, Object> bookingDetails(BookingException refusal) {
        if (refusal instanceof SeatAlreadyBookedException e) {
            return details(
                    "seatId", e.getSeatId(),
                    "seatLabel", e.getSeatLabel(),
                    "bookingDate", e.getBookingDate(),
                    "takenByUserId", e.getTakenByUserId(),
                    "takenByDisplayName", e.getTakenByDisplayName());
        }
        if (refusal instanceof AlreadyBookedThatDayException e) {
            return details(
                    "bookingDate", e.getBookingDate(),
                    "existingBookingId", e.getExistingBookingId(),
                    "existingSeatId", e.getExistingSeatId(),
                    "existingSeatLabel", e.getExistingSeatLabel());
        }
        if (refusal instanceof SeatReservedForTeamException e) {
            return details(
                    "seatId", e.getSeatId(),
                    "seatLabel", e.getSeatLabel(),
                    "bookingDate", e.getBookingDate(),
                    "teamId", e.getTeamId(),
                    "teamName", e.getTeamName(),
                    "releaseAtTime", e.getReleaseAtTime());
        }
        if (refusal instanceof DateOutsideBookingWindowException e) {
            return details(
                    "requestedDate", e.getRequestedDate(),
                    "earliestAllowed", e.getEarliestAllowed(),
                    "latestAllowed", e.getLatestAllowed());
        }
        if (refusal instanceof SeatNotBookableException e) {
            return details("seatId", e.getSeatId(), "seatLabel", e.getSeatLabel(), "seatStatus", e.getSeatStatus());
        }
        if (refusal instanceof BookingNotActiveException e) {
            return details("bookingId", e.getBookingId(), "status", e.getStatus());
        }
        if (refusal instanceof AlreadyCheckedInException e) {
            return details("bookingId", e.getBookingId(), "checkedInAt", e.getCheckedInAt());
        }
        if (refusal instanceof CheckInNotForTodayException e) {
            return details("bookingId", e.getBookingId(), "bookingDate", e.getBookingDate(), "today", e.getToday());
        }
        if (refusal instanceof IdempotencyKeyConflictException e) {
            return details(
                    "idempotencyKey", e.getIdempotencyKey(),
                    "originalBookingId", e.getOriginalBooking().id(),
                    "requestedUserId", e.getRequestedUserId(),
                    "requestedSeatId", e.getRequestedSeatId(),
                    "requestedDate", e.getRequestedDate());
        }
        if (refusal instanceof BookingNotFoundException e) {
            return details("bookingId", e.getBookingId());
        }
        if (refusal instanceof SeatNotFoundException e) {
            return details("seatId", e.getSeatId());
        }
        if (refusal instanceof BookingUserNotFoundException e) {
            return details("userId", e.getUserId());
        }
        return null;
    }

    // ─── Reservation (team hold) domain ───────────────────────────────────────────

    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<AuthErrorResponse> handleReservationFailure(ReservationException refusal,
                                                                      HttpServletRequest request) {
        ReservationErrorCode code = refusal.errorCode();
        return respond(reservationStatus(code), code.name(), reservationMessage(code),
                reservationDetails(refusal), request);
    }

    private static HttpStatus reservationStatus(ReservationErrorCode code) {
        return switch (code) {
            case TEAM_NOT_FOUND, SEAT_NOT_FOUND, RESERVATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_RESERVATION_RANGE -> HttpStatus.BAD_REQUEST;
            case RESERVATION_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
        };
    }

    private static String reservationMessage(ReservationErrorCode code) {
        return switch (code) {
            case TEAM_NOT_FOUND -> "No team was found with that id.";
            case SEAT_NOT_FOUND -> "No seat was found with that id.";
            case RESERVATION_NOT_FOUND -> "No reservation was found with that id.";
            case INVALID_RESERVATION_RANGE -> "The reservation end date must not be before the start date.";
            case RESERVATION_ACCESS_DENIED -> ACCESS_DENIED_MESSAGE;
        };
    }

    /** {@code null} for {@link ReservationAccessDeniedException}, for the same reason as above. */
    private static Map<String, Object> reservationDetails(ReservationException refusal) {
        if (refusal instanceof TeamNotFoundException e) {
            return details("teamId", e.getTeamId());
        }
        if (refusal instanceof ReservationSeatNotFoundException e) {
            return details("seatId", e.getSeatId());
        }
        if (refusal instanceof ReservationNotFoundException e) {
            return details("reservationId", e.getReservationId());
        }
        if (refusal instanceof InvalidReservationRangeException e) {
            return details("startDate", e.getStartDate(), "endDate", e.getEndDate());
        }
        return null;
    }

    // ─── Bean Validation and malformed requests ───────────────────────────────────

    /**
     * A malformed body, at 400. Field errors travel in {@code details} — specific enough for a
     * client to highlight the offending field — while the top-level {@code message} stays generic:
     * on the login endpoint, a detailed top-level rejection is the one place that could start to
     * describe what a valid account looks like.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthErrorResponse> handleInvalidBody(MethodArgumentNotValidException invalid,
                                                               HttpServletRequest request) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : invalid.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(),
                    error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, AuthErrorCode.VALIDATION_FAILED.name(), VALIDATION_MESSAGE,
                fieldErrors.isEmpty() ? null : fieldErrors, request);
    }

    /** A query or path parameter that will not convert — {@code ?date=not-a-date} and the like. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AuthErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException mismatch,
                                                                HttpServletRequest request) {
        String expected = mismatch.getRequiredType() == null ? "a different type"
                : "a valid " + mismatch.getRequiredType().getSimpleName();
        return respond(HttpStatus.BAD_REQUEST, AuthErrorCode.VALIDATION_FAILED.name(), INVALID_PARAMETER_MESSAGE,
                details(mismatch.getName(), "must be " + expected), request);
    }

    // ─── Shared ────────────────────────────────────────────────────────────────────

    private ResponseEntity<AuthErrorResponse> respond(HttpStatus status,
                                                      String code,
                                                      String message,
                                                      Map<String, Object> details,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                new AuthErrorResponse(code, message, request.getRequestURI(), officeClock.timestamp(), details));
    }

    private static Map<String, Object> details(Object... keyValuePairs) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            details.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return details;
    }
}
