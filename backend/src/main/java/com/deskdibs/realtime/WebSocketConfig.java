package com.deskdibs.realtime;

import com.deskdibs.common.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * The STOMP endpoint clients open to subscribe to {@code /topic/seatmap/{date}}.
 *
 * <p>{@code /ws} is permitted unauthenticated in {@code SecurityConfig} — see that file's javadoc
 * for why — and every connection is authenticated one layer up instead, by
 * {@link StompAuthChannelInterceptor} on the client's first STOMP frame. Allowed origins are the
 * same {@link CorsProperties} the REST API's CORS filter reads, so the socket and the API share one
 * trust boundary rather than two that could quietly drift apart.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;
    private final CorsProperties cors;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor, CorsProperties cors) {
        this.authInterceptor = authInterceptor;
        this.cors = cors;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(cors.allowedOrigins().toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
