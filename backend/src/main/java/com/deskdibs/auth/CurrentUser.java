package com.deskdibs.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Who is calling, for the application to ask.
 *
 * <p>The one bridge between the security layer and everything else. Services take an
 * {@code actingUserId} as a parameter — {@code BookingService} deliberately does, which is what
 * keeps it testable without a security context — and this is where a Phase 4 controller gets that
 * id from:
 *
 * <pre>{@code
 * @PostMapping("/api/bookings")
 * BookingResponse claim(@Valid @RequestBody ClaimRequest body) {
 *     return BookingResponse.of(bookings.claim(currentUser.requireId(), body.seatId(), ...));
 * }
 * }</pre>
 *
 * <p>Wrapping {@code SecurityContextHolder} rather than letting it be read all over the codebase
 * keeps a static thread-local out of business logic, gives the identity seam one name, and means
 * the two providers are invisible past this line: what comes out is an
 * {@link AuthenticatedUser}, whichever one produced it.
 */
@Component
public class CurrentUser {

    /** The caller, or empty when the request is anonymous. */
    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser user
                ? Optional.of(user)
                : Optional.empty();
    }

    /**
     * The caller on an endpoint that requires one.
     *
     * <p>Throwing rather than returning null keeps a mistake loud: an
     * {@code AuthenticationException} thrown from a handler is caught by
     * {@code ExceptionTranslationFilter} and leaves as 401 JSON, so even an endpoint accidentally
     * left off the authenticated list still fails closed instead of running with no user.
     */
    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new UnauthenticatedException(
                "no authenticated principal on a request that requires one"));
    }

    /** The database id to hand to a service's {@code actingUserId} parameter. */
    public long requireId() {
        return require().id();
    }
}
