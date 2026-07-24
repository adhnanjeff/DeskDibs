package com.deskdibs.common;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Which browser origins may call this API, bound from {@code deskdibs.cors.allowed-origins}
 * (env {@code CORS_ALLOWED_ORIGINS}, a comma-separated list).
 *
 * <p>Exact origins only — never a wildcard. {@link org.springframework.web.cors.CorsConfiguration}
 * refuses at startup to combine {@code allowCredentials(true)} with an allowed origin of {@code *},
 * which is exactly the footgun this record exists to keep out of reach: the default is the Vite
 * dev server's own origin, and a real deployment sets the env var to its actual frontend origin(s)
 * rather than reaching for a wildcard to make CORS errors go away.
 *
 * @param allowedOrigins exact origins (scheme + host + port), e.g. {@code http://localhost:5173}
 */
@ConfigurationProperties(prefix = "deskdibs.cors")
@Validated
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
