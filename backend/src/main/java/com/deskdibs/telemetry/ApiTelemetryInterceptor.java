package com.deskdibs.telemetry;

import com.deskdibs.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Publishes one {@link ApiCallEvent} per finished {@code /api/**} call to the admin telemetry topic.
 *
 * <h2>Why an interceptor and not a filter</h2>
 * By {@code afterCompletion} Spring has already resolved the handler, so
 * {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} holds the templated mapping. A servlet
 * filter runs outside that and would only ever see the concrete URI, which is both noisier to
 * aggregate and the thing we specifically do not want to broadcast.
 *
 * <h2>Telemetry never breaks the request</h2>
 * Everything here runs after the response is written, and every publish is wrapped: a broker that
 * is down, full or slow must not turn a successful booking into a 500. A dropped frame costs the
 * admin view one dot.
 *
 * <p>There is no feedback loop to guard against — the broadcast leaves over STOMP, which is not an
 * HTTP request and so never re-enters this interceptor.
 */
@Component
public class ApiTelemetryInterceptor implements HandlerInterceptor {

    /** Admins only; enforced on SUBSCRIBE by {@code StompAuthChannelInterceptor}. */
    public static final String TOPIC = "/topic/admin/telemetry";

    private static final Logger log = LoggerFactory.getLogger(ApiTelemetryInterceptor.class);
    private static final String START_NANOS = ApiTelemetryInterceptor.class.getName() + ".start";
    private static final String ANONYMOUS = "anonymous";
    private static final String UNMATCHED_ROUTE = "unmatched";

    private final SimpMessagingTemplate messagingTemplate;

    public ApiTelemetryInterceptor(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_NANOS, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            messagingTemplate.convertAndSend(TOPIC, toEvent(request, response));
        } catch (RuntimeException broadcastFailed) {
            // Deliberately swallowed — see the class javadoc. Logged at debug because a broker
            // outage would otherwise fill the log with one line per request.
            log.debug("Could not broadcast API telemetry", broadcastFailed);
        }
    }

    private ApiCallEvent toEvent(HttpServletRequest request, HttpServletResponse response) {
        int status = response.getStatus();
        return new ApiCallEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                request.getMethod(),
                route(request),
                status,
                elapsedMillis(request),
                outcomeOf(status),
                TelemetryLane.of(route(request)).name(),
                actor());
    }

    /**
     * The templated mapping Spring matched. Falls back to a constant rather than the raw URI: an
     * unmatched path is attacker-controlled text, and echoing it into an admin's browser is how a
     * monitoring view becomes an injection surface.
     */
    private String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern instanceof String matched && !matched.isBlank() ? matched : UNMATCHED_ROUTE;
    }

    private long elapsedMillis(HttpServletRequest request) {
        Object start = request.getAttribute(START_NANOS);
        if (!(start instanceof Long startNanos)) {
            return 0L;
        }
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private String outcomeOf(int status) {
        if (status >= 500) {
            return "SERVER_ERROR";
        }
        if (status >= 400) {
            return "CLIENT_ERROR";
        }
        return "OK";
    }

    /**
     * The caller's display name, read from the {@link AuthenticatedUser} the security chain
     * resolved from the database — not from a token claim.
     */
    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.displayName();
        }
        return ANONYMOUS;
    }

    /** Which lane of the flow diagram a route belongs to. */
    enum TelemetryLane {
        AUTH,
        SEATMAP,
        BOOKING,
        RESERVATION,
        ADMIN,
        OTHER;

        static TelemetryLane of(String route) {
            String path = route.toLowerCase(Locale.ROOT);
            if (path.startsWith("/api/auth")) {
                return AUTH;
            }
            if (path.startsWith("/api/admin")) {
                return ADMIN;
            }
            if (path.startsWith("/api/reservations")) {
                return RESERVATION;
            }
            if (path.startsWith("/api/bookings")) {
                return BOOKING;
            }
            if (path.startsWith("/api/seatmap") || path.startsWith("/api/seats")) {
                return SEATMAP;
            }
            return OTHER;
        }
    }
}
