package com.deskdibs.auth;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two ways a misconfigured deployment is stopped before it can serve a request.
 *
 * <p>Both are startup decisions, so both are tested as what they are — a function of an
 * {@code Environment}, and a property binding — rather than by booting contexts that are designed
 * to fail. A context that refuses to start is awkward to assert on and slow to run; the rules
 * themselves are neither.
 */
class AuthStartupGuardsTest {

    // ─── A typo in AUTH_PROVIDER stops the application ───────────────────────────

    @Test
    @DisplayName("an unrecognised AUTH_PROVIDER stops binding, quoting the value that was wrong")
    void anUnrecognisedProviderFailsToBind() {
        assertThatThrownBy(() -> bindProvider("entar"))
                .isInstanceOf(BindException.class)
                .hasStackTraceContaining("entar");
    }

    @Test
    @DisplayName("a blank AUTH_PROVIDER is not quietly treated as the development provider")
    void aBlankProviderIsNotQuietlyTreatedAsLocal() {
        // A missing environment variable defaulting to `local` is the exact accident this guards:
        // an application accepting development passwords because a deployment forgot a line.
        // Nothing binds at all, so the record is created with a null provider.
        assertThat(bindProviderOrNull("")).isNull();

        // Binding leaves it null, and @NotNull turns null into a startup failure.
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new AuthProperties(null)))
                    .as("a null provider must fail validation, which stops the context")
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("both spellings of each provider bind, so casing in a deployment file is not a trap")
    void bothProvidersBindCaseInsensitively() {
        assertThat(bindProvider("local")).isEqualTo(AuthProviderKind.LOCAL);
        assertThat(bindProvider("LOCAL")).isEqualTo(AuthProviderKind.LOCAL);
        assertThat(bindProvider("entra")).isEqualTo(AuthProviderKind.ENTRA);
        assertThat(bindProvider("ENTRA")).isEqualTo(AuthProviderKind.ENTRA);
    }

    // ─── The development provider refuses to run in production ───────────────────

    @Test
    @DisplayName("the local provider refuses to start under a production profile")
    void theLocalProviderRefusesToStartInProduction() {
        for (String profile : new String[]{"prod", "production", "PROD"}) {
            assertThatThrownBy(() ->
                    LocalProductionProfileGuard.assertNotProduction(new MockEnvironment().withProperty(
                            "spring.profiles.active", profile)))
                    .as("profile '%s' should stop the local provider", profile)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AUTH_PROVIDER=local")
                    .hasMessageContaining("AUTH_PROVIDER=entra");
        }
    }

    @Test
    @DisplayName("a production profile alongside others is still caught")
    void aProductionProfileAmongOthersIsStillCaught() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "azure,prod,metrics");

        assertThatThrownBy(() -> LocalProductionProfileGuard.assertNotProduction(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ordinary development profiles start normally")
    void ordinaryDevelopmentProfilesStartNormally() {
        assertThatCode(() -> LocalProductionProfileGuard.assertNotProduction(new MockEnvironment()))
                .doesNotThrowAnyException();
        assertThatCode(() -> LocalProductionProfileGuard.assertNotProduction(
                new MockEnvironment().withProperty("spring.profiles.active", "test,local")))
                .doesNotThrowAnyException();
    }

    private static AuthProviderKind bindProvider(String configured) {
        return bind(configured).get().provider();
    }

    private static AuthProviderKind bindProviderOrNull(String configured) {
        AuthProperties bound = bind(configured).orElse(null);
        return bound == null ? null : bound.provider();
    }

    private static BindResult<AuthProperties> bind(String configured) {
        return new Binder(new MapConfigurationPropertySource(
                Map.of("deskdibs.auth.provider", configured)))
                .bind("deskdibs.auth", AuthProperties.class);
    }
}
