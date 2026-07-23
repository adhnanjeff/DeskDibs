package com.deskdibs.auth;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Which identity provider this instance runs.
 *
 * <p>Typed as an enum on purpose. {@code AUTH_PROVIDER=entar} cannot bind to
 * {@link AuthProviderKind}, so the application refuses to start with a message naming the
 * property — instead of starting with neither provider wired, or worse, silently falling back to
 * the development one and accepting dev passwords in production.
 *
 * @param provider {@code local} for development, {@code entra} for Microsoft SSO
 */
@ConfigurationProperties(prefix = "deskdibs.auth")
@Validated
public record AuthProperties(@NotNull AuthProviderKind provider) {
}
