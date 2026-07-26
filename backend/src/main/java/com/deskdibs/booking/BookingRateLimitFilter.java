package com.deskdibs.booking;

import com.deskdibs.auth.AuthErrorCode;
import com.deskdibs.auth.AuthErrorWriter;
import com.deskdibs.auth.AuthenticatedUser;
import com.deskdibs.common.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Slows a single account down to a human rate on the booking endpoints (PLAN.md §5 #14).
 *
 * <h2>What this is not</h2>
 * It is <em>not</em> what stops double bookings. That is the partial unique index in Postgres, and
 * only that. This filter exists so one scripted client cannot occupy the connection pool racing
 * itself; switch it off and the invariant still holds, which is why {@code enabled} is a plain
 * toggle and why the concurrency tests deliberately run with it off — 150 threads claiming one seat
 * is the behaviour under test, not abuse to be throttled.
 *
 * <h2>Keyed by user, not by IP</h2>
 * An office behind one NAT would share an IP, so an IP bucket would throttle a whole floor because
 * one person was impatient. The key is the authenticated user id, which is also why this filter
 * sits <em>after</em> authentication in the chain.
 *
 * <h2>Buckets expire</h2>
 * Buckets live in a Caffeine cache with an idle TTL rather than a plain map, so the process does not
 * accumulate one bucket per account that has ever logged in.
 */
@Component
public class BookingRateLimitFilter extends OncePerRequestFilter {

    private static final String BOOKINGS_PREFIX = "/api/bookings";

    private final RateLimitProperties properties;
    private final AuthErrorWriter errorWriter;
    private final Cache<Long, Bucket> buckets;

    public BookingRateLimitFilter(RateLimitProperties properties, AuthErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(properties.bucketTtl().toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(10_000)
                .build();
    }

    /**
     * Only the four mutations. Reads — the seat map, your own bookings — are cheap, are what a
     * dashboard left open all day does constantly, and throttling them would break the product
     * without protecting anything.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith(BOOKINGS_PREFIX)) {
            return true;
        }
        return HttpMethod.GET.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Long userId = currentUserId();
        if (userId == null) {
            // Unauthenticated: there is no account to throttle, and the security chain is about to
            // refuse this anyway. Nothing to do but get out of the way.
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.get(userId, id -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setHeader("X-RateLimit-Remaining", "0");
        errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS.value(),
                AuthErrorCode.RATE_LIMITED,
                "Too many booking requests. Try again in " + retryAfterSeconds + " second"
                        + (retryAfterSeconds == 1 ? "." : "s."));
    }

    /**
     * A full minute's allowance, refilled smoothly rather than all at once on the minute — a
     * greedy refill would let a client burn the whole budget, wait for the tick, and burn it again,
     * which is the behaviour this is meant to stop.
     */
    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.bookingOpsPerMinute())
                        .refillIntervally(properties.bookingOpsPerMinute(), Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        return null;
    }
}
