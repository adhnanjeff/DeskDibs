package com.deskdibs.realtime;

import com.deskdibs.auth.AuthProvider;
import com.deskdibs.auth.AuthenticatedUserToken;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authenticates the STOMP {@code CONNECT} frame with the same {@link JwtDecoder} and
 * {@link AuthProvider} the REST API uses — no separate validation logic, no separate rules.
 *
 * <p>The raw WebSocket handshake carries no identity: a browser's native WebSocket API cannot
 * attach an {@code Authorization} header to it, which is why {@code /ws} is permitted
 * unauthenticated in {@code SecurityConfig}. This interceptor is the actual authentication boundary
 * for the socket instead. A STOMP client (browser or test) sends the same bearer token it uses for
 * REST as a native {@code Authorization} header on the {@code CONNECT} frame — the first message it
 * sends immediately after the socket opens; a session that omits it, or presents one that fails
 * decoding or resolves to nobody usable, is refused here, before it can subscribe to anything.
 * Occupancy data — colleagues' names against seats — never reaches a socket that has not proven who
 * it is.
 *
 * <p>{@code CONNECT} proves identity. Once it succeeds, Spring's STOMP session keeps the
 * {@link org.springframework.security.core.Authentication} this method attaches for the life of the
 * session, so a later {@code SUBSCRIBE} does not need to re-prove <em>who</em> it is.
 *
 * <h2>Why SUBSCRIBE is inspected too</h2>
 * The broker is Spring's simple in-memory broker, which applies no authorization of its own: any
 * authenticated session may subscribe to any destination it can name. That is fine for
 * {@code /topic/seatmap/{date}}, which every signed-in colleague is entitled to see, and wrong for
 * {@code /topic/admin/**}, which carries operational telemetry. So destinations under
 * {@code /topic/admin/} are gated here, on the authority derived from {@code app_user.role} —
 * never from a claim on the token. Without this the REST tier's {@code hasRole('ADMIN')} would be
 * trivially side-stepped by opening a socket instead.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String MISSING_TOKEN_MESSAGE = "A bearer token is required to connect.";
    private static final String INVALID_TOKEN_MESSAGE = "The bearer token is invalid or expired.";
    private static final String BEARER_PREFIX = "Bearer ";

    /** Destinations under here are administrators-only. */
    private static final String ADMIN_TOPIC_PREFIX = "/topic/admin/";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String FORBIDDEN_MESSAGE = "Administrator access is required for that topic.";

    private final JwtDecoder jwtDecoder;
    private final AuthProvider authProvider;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder, AuthProvider authProvider) {
        this.jwtDecoder = jwtDecoder;
        this.authProvider = authProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Jwt token = decode(accessor);
            accessor.setUser(new AuthenticatedUserToken(authProvider.resolve(token), token));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    /** Fails closed: an unnamed destination, or a session with no principal, is refused. */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(ADMIN_TOPIC_PREFIX)) {
            return;
        }
        Principal principal = accessor.getUser();
        if (!(principal instanceof Authentication authentication)
                || authentication.getAuthorities().stream()
                        .noneMatch(granted -> ADMIN_AUTHORITY.equals(granted.getAuthority()))) {
            throw new AccessDeniedException(FORBIDDEN_MESSAGE);
        }
    }

    private Jwt decode(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BadCredentialsException(MISSING_TOKEN_MESSAGE);
        }
        try {
            return jwtDecoder.decode(header.substring(BEARER_PREFIX.length()).trim());
        } catch (JwtException invalid) {
            throw new BadCredentialsException(INVALID_TOKEN_MESSAGE, invalid);
        }
    }
}
