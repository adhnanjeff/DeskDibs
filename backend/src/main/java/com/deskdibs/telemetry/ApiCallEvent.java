package com.deskdibs.telemetry;

import java.time.Instant;

/**
 * One finished HTTP call, broadcast to {@code /topic/admin/telemetry} for the admin API view.
 *
 * <h2>What this deliberately does not carry</h2>
 * No request body, no response body, no headers, no bearer token, no query string, and no raw URI.
 * {@link #route()} is the <em>templated</em> mapping Spring matched ({@code /api/bookings/{id}}),
 * not the concrete path, so identifiers never travel and calls aggregate into lanes on their own.
 * This is a picture of traffic shape, not an audit log — {@code ReleasedBookingView} and friends
 * remain the record of who did what.
 *
 * <p>{@link #actor()} is a display name because the audience is administrators, who can already see
 * the people list. It is the one identifying field here and it is not a join key.
 *
 * @param id          a per-call key so a client can animate one request without re-keying on every frame
 * @param at          when the call finished, server clock — never the browser's
 * @param method      HTTP verb
 * @param route       the templated mapping, or {@code unmatched} when nothing handled it
 * @param status      HTTP status actually written
 * @param durationMs  wall time from the start of the interceptor chain to completion
 * @param outcome     {@code OK}, {@code CLIENT_ERROR} or {@code SERVER_ERROR}
 * @param lane        which flow the call belongs to, so the diagram can route it
 * @param actor       the authenticated caller's display name, or {@code anonymous}
 */
public record ApiCallEvent(
        String id,
        Instant at,
        String method,
        String route,
        int status,
        long durationMs,
        String outcome,
        String lane,
        String actor) {
}
