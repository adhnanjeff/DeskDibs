package com.deskdibs.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Asserts at startup that the provider that came up is the provider that was asked for.
 *
 * <p>The two implementations are chosen by {@code @ConditionalOnProperty}, and a condition that
 * quietly matches nothing produces a context with no {@link AuthProvider} at all. Depending on
 * one here turns that into a startup failure — the application cannot serve a single request with
 * no way to identify a caller, so it must not start pretending it can.
 *
 * <p>The equality check catches the subtler version: a condition edited to spell a value
 * differently from the enum it stands for, leaving {@code AUTH_PROVIDER=entra} running the
 * development provider. That is precisely the accident this whole package is arranged to prevent,
 * so it is worth one comparison and one log line at boot — a line an operator can also use to
 * confirm, from the logs of a deployment they did not perform, which identity provider is live.
 */
@Component
public class ActiveAuthProviderGuard {

    private static final Logger log = LoggerFactory.getLogger(ActiveAuthProviderGuard.class);

    public ActiveAuthProviderGuard(AuthProvider active, AuthProperties properties) {
        if (active.kind() != properties.provider()) {
            throw new IllegalStateException(
                    "Configured auth provider is " + properties.provider()
                            + " but the active implementation is " + active.kind()
                            + ". Check the @ConditionalOnProperty values in the auth package.");
        }
        log.info("DeskDibs authentication provider: {}", active.kind());
    }
}
