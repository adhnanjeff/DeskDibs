package com.deskdibs.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The one {@link CorsConfigurationSource} the API and the WebSocket handshake both read from.
 *
 * <p>Bearer tokens, not cookies, cross this API's origin boundary, but {@code allowCredentials} is
 * still turned on: some HTTP clients send {@code credentials: 'include'} regardless, and turning it
 * on is only safe because {@link CorsProperties} never permits a wildcard origin — Spring's
 * {@link CorsConfiguration} refuses to start with the wildcard-plus-credentials combination, which
 * is exactly the mistake this split (explicit origins here, credentials allowed here, never {@code
 * *} anywhere) is designed to make structurally impossible rather than merely documented against.
 */
@Configuration(proxyBeanMethods = false)
public class WebCorsConfiguration {

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private static final List<String> ALLOWED_HEADERS =
            List.of("Authorization", "Content-Type", "Idempotency-Key");

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
