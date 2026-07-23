package com.deskdibs.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Every 401 the application emits.
 *
 * <p>Replaces both defaults that would otherwise apply — Spring Security's
 * {@code BearerTokenAuthenticationEntryPoint}, which sends an empty body, and the servlet
 * container's error page, which sends HTML.
 *
 * <p>Two rules shape what comes out. Refusals this package raised know their own
 * {@link AuthErrorCode} and carry wording already vetted as safe to show a stranger. Everything
 * else — a bad signature, an expired token, a malformed bearer header, all of which arrive as
 * Nimbus or Spring exceptions — collapses to a single {@link AuthErrorCode#INVALID_TOKEN} with
 * fixed wording. The underlying exception's message is logged, never returned: it can name
 * algorithms, claim values and key ids, and none of that is a stranger's business.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);

    /** A bearer challenge with no error detail: enough for a client, nothing for a prober. */
    private static final String CHALLENGE = "Bearer";

    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token.";
    private static final String UNAUTHENTICATED_MESSAGE = "Authentication is required.";

    private final AuthErrorWriter errors;

    public JsonAuthenticationEntryPoint(AuthErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException failure) throws IOException {

        AuthErrorCode code;
        String message;
        if (failure instanceof AuthException known) {
            code = known.errorCode();
            message = known.publicMessage();
        } else if (hasBearerToken(request)) {
            code = AuthErrorCode.INVALID_TOKEN;
            message = INVALID_TOKEN_MESSAGE;
        } else {
            code = AuthErrorCode.UNAUTHENTICATED;
            message = UNAUTHENTICATED_MESSAGE;
        }

        log.debug("401 {} on {} {}: {}", code, request.getMethod(), request.getRequestURI(),
                failure.getMessage());

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        errors.write(request, response, HttpStatus.UNAUTHORIZED.value(), code, message);
    }

    /** A request that presented something is told its token is bad; one that presented nothing is not. */
    private static boolean hasBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
    }
}
