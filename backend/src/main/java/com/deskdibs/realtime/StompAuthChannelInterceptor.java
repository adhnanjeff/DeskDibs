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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

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
 * <p>Only {@code CONNECT} is inspected. Once it succeeds, Spring's STOMP session keeps the
 * {@link org.springframework.security.core.Authentication} this method attaches for the life of the
 * session, so later {@code SUBSCRIBE} frames on the same socket do not need to re-prove anything.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String MISSING_TOKEN_MESSAGE = "A bearer token is required to connect.";
    private static final String INVALID_TOKEN_MESSAGE = "The bearer token is invalid or expired.";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final AuthProvider authProvider;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder, AuthProvider authProvider) {
        this.jwtDecoder = jwtDecoder;
        this.authProvider = authProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            Jwt token = decode(accessor);
            accessor.setUser(new AuthenticatedUserToken(authProvider.resolve(token), token));
        }
        return message;
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
