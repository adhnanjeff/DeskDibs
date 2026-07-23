package com.deskdibs.auth;

import com.deskdibs.common.OfficeClock;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The application's single {@code @RestControllerAdvice}.
 *
 * <p>It handles the refusals that come out of a <em>handler</em> — a login that failed, a body
 * that would not validate. The refusals decided before any handler runs (no token, bad token,
 * wrong role) never reach here, because at that point in the filter chain there is no controller
 * to advise; those are written by {@link JsonAuthenticationEntryPoint} and
 * {@link JsonAccessDeniedHandler}. Both routes emit the same {@link AuthErrorResponse} shape, so
 * the split is invisible to a client.
 *
 * <p>Phase 4 folds the booking exceptions into this class rather than adding a second advice —
 * {@code BookingErrorCode} already carries the same stable-identity contract as
 * {@link AuthErrorCode}, so they merge into one wire vocabulary without either side changing.
 *
 * <p>Nothing here ever returns an exception's own message. A stack trace, a class name, or a raw
 * SQL fragment reaching a client is a leak, and the way that happens is by someone writing
 * {@code e.getMessage()} into a response body once.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final String VALIDATION_MESSAGE = "The request body is not valid.";

    private final OfficeClock officeClock;

    public AuthExceptionHandler(OfficeClock officeClock) {
        this.officeClock = officeClock;
    }

    /**
     * Everything this package raises from inside a handler.
     *
     * <p>Chiefly {@link InvalidCredentialsException} from a failed login — 401, with wording that
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
        return respond(status, refusal.errorCode(), refusal.publicMessage(), request);
    }

    /**
     * A malformed body, at 400. The field errors are deliberately not enumerated: on the login
     * endpoint that is the one place where a detailed rejection could start to describe what a
     * valid account looks like, and no client needs the detail to fix a blank password.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthErrorResponse> handleInvalidBody(MethodArgumentNotValidException invalid,
                                                               HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, AuthErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, request);
    }

    private ResponseEntity<AuthErrorResponse> respond(HttpStatus status,
                                                      AuthErrorCode code,
                                                      String message,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                new AuthErrorResponse(code, message, request.getRequestURI(), officeClock.timestamp()));
    }
}
