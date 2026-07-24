package com.deskdibs.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * The JSON body of a refusal.
 *
 * <p>401 and 403 are the two responses most likely to be produced by a container default rather
 * than by application code, which is how an HTML error page or a stack trace ends up in front of a
 * client that asked for JSON. Both are written explicitly from this record instead.
 *
 * <p>One shape for both {@link AuthErrorCode} and {@code BookingErrorCode} failures — Phase 4 folds
 * the booking domain's exceptions into {@link AuthExceptionHandler} rather than adding a second
 * advice, so {@code code} is a plain {@code String} (either enum's {@code name()}) rather than
 * typed to one of them. {@code details} carries the structured fields those exceptions were
 * deliberately built with — a seat label, the winner's display name, an allowed date range — so a
 * client can branch on data instead of parsing a sentence. It is {@code null}, and omitted from the
 * JSON entirely, whenever an exception carries nothing beyond its code and message.
 *
 * @param code      stable machine-readable identity of the failure
 * @param message   safe to display; reveals nothing about which accounts exist
 * @param path      the request URI that was refused
 * @param timestamp when the refusal was written, in the office timezone
 * @param details   exception-specific structured fields, or {@code null} when there are none
 */
public record AuthErrorResponse(
        String code,
        String message,
        String path,
        OffsetDateTime timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> details) {
}
