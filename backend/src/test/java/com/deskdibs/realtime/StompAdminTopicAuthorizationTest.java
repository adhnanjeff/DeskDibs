package com.deskdibs.realtime;

import com.deskdibs.auth.AuthProvider;
import com.deskdibs.auth.AuthenticatedUser;
import com.deskdibs.auth.AuthenticatedUserToken;
import com.deskdibs.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The admin telemetry topic is gated on the STOMP {@code SUBSCRIBE} frame.
 *
 * <p>This matters more than it looks. The broker is Spring's simple in-memory broker, which
 * authorizes nothing: every authenticated session may subscribe to any destination it can name. So
 * {@code hasRole('ADMIN')} on the REST tier is not enough on its own — without the check these
 * tests cover, any signed-in employee could open a socket and watch the whole office's traffic.
 */
class StompAdminTopicAuthorizationTest {

    private static final String ADMIN_TOPIC = "/topic/admin/telemetry";
    private static final String SEAT_MAP_TOPIC = "/topic/seatmap/2026-07-27";

    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(mock(JwtDecoder.class), mock(AuthProvider.class));

    @Test
    @DisplayName("an administrator may subscribe to the admin telemetry topic")
    void adminMaySubscribeToAdminTopic() {
        Message<byte[]> subscribe = subscribeFrame(ADMIN_TOPIC, UserRole.ADMIN);

        assertThatCode(() -> interceptor.preSend(subscribe, mock(org.springframework.messaging.MessageChannel.class)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an employee subscribing to the admin telemetry topic is refused")
    void employeeMayNotSubscribeToAdminTopic() {
        Message<byte[]> subscribe = subscribeFrame(ADMIN_TOPIC, UserRole.EMPLOYEE);

        assertThatThrownBy(() -> interceptor.preSend(subscribe, mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a manager subscribing to the admin telemetry topic is refused")
    void managerMayNotSubscribeToAdminTopic() {
        Message<byte[]> subscribe = subscribeFrame(ADMIN_TOPIC, UserRole.MANAGER);

        assertThatThrownBy(() -> interceptor.preSend(subscribe, mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a session with no principal is refused the admin telemetry topic")
    void anonymousSessionIsRefusedAdminTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(ADMIN_TOPIC);
        Message<byte[]> subscribe = org.springframework.messaging.support.MessageBuilder
                .createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(subscribe, mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an employee may still subscribe to a seat map topic")
    void employeeMayStillSubscribeToSeatMap() {
        Message<byte[]> subscribe = subscribeFrame(SEAT_MAP_TOPIC, UserRole.EMPLOYEE);

        assertThatCode(() -> interceptor.preSend(subscribe, mock(org.springframework.messaging.MessageChannel.class)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a subscribe frame with no destination is refused nothing but reaches no admin topic")
    void subscribeWithoutDestinationIsLeftAlone() {
        Message<byte[]> subscribe = subscribeFrame(null, UserRole.EMPLOYEE);

        assertThatCode(() -> interceptor.preSend(subscribe, mock(org.springframework.messaging.MessageChannel.class)))
                .doesNotThrowAnyException();
        assertThat(ADMIN_TOPIC).startsWith("/topic/admin/");
    }

    private Message<byte[]> subscribeFrame(String destination, UserRole role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        // The authority comes from app_user.role, exactly as the CONNECT frame would have set it.
        AuthenticatedUser user = new AuthenticatedUser(7L, "someone@deskdibs.local", "Someone", role);
        accessor.setUser(new AuthenticatedUserToken(user, stubToken()));
        return org.springframework.messaging.support.MessageBuilder
                .createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /** Identity evidence only — never an input to the rule under test. */
    private Jwt stubToken() {
        return Jwt.withTokenValue("stub")
                .header("alg", "HS256")
                .subject("7")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }
}
