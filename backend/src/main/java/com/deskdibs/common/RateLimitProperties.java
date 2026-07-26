package com.deskdibs.common;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Per-user throttling of booking mutations, bound from {@code deskdibs.rate-limit}.
 *
 * <p>PLAN.md §5 #14: a scripted client hammering the claim endpoint should be slowed down rather
 * than allowed to spend the whole connection pool racing itself. This is deliberately *not* a
 * correctness control — the anti-double-booking guarantee is the database's partial unique index
 * and nothing else. Turning this off cannot produce a double booking; it only removes the
 * politeness limit.
 *
 * @param enabled          off switch, so a load test can measure the system rather than the limiter
 * @param bookingOpsPerMinute how many claim/move/cancel/check-in calls one user may make per minute
 * @param bucketTtl        how long an idle user's bucket is kept before it is evicted. Long enough
 *                         that a returning user is still throttled, short enough that the map does
 *                         not grow with every account that ever logged in.
 */
@ConfigurationProperties(prefix = "deskdibs.rate-limit")
@Validated
public record RateLimitProperties(

        boolean enabled,

        @Min(1) int bookingOpsPerMinute,

        Duration bucketTtl) {

    public RateLimitProperties {
        bucketTtl = bucketTtl == null ? Duration.ofMinutes(10) : bucketTtl;
    }
}
