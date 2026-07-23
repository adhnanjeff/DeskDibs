package com.deskdibs.auth;

import java.time.OffsetDateTime;

/**
 * The JSON body of a refusal.
 *
 * <p>401 and 403 are the two responses most likely to be produced by a container default rather
 * than by application code, which is how an HTML error page or a stack trace ends up in front of a
 * client that asked for JSON. Both are written explicitly from this record instead.
 *
 * <p>Shaped to match what Phase 4 will build from {@code BookingErrorCode}: a stable
 * {@code code} to branch on, a {@code message} safe to show a user, and the {@code path} for
 * support. Nothing derived from an exception's own message, no stack trace, no SQL, no class name.
 *
 * @param code      stable machine-readable identity of the failure
 * @param message   safe to display; reveals nothing about which accounts exist
 * @param path      the request URI that was refused
 * @param timestamp when the refusal was written, in the office timezone
 */
public record AuthErrorResponse(
        AuthErrorCode code,
        String message,
        String path,
        OffsetDateTime timestamp) {
}
