package com.deskdibs.auth;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The filter chain. Deny by default; open only what is named here.
 *
 * <h2>Fail closed</h2>
 * The chain ends in {@code anyRequest().authenticated()}, so an endpoint added in a later phase is
 * protected the moment it exists. Making it public is a deliberate edit to this file, reviewable
 * as a diff — the opposite of the arrangement where forgetting to annotate a new controller
 * leaves it open. The permitted list is four things: local login, the health probe, and the
 * OpenAPI documents Phase 4 will publish.
 *
 * <h2>Provider-agnostic</h2>
 * Nothing here mentions {@code local} or {@code entra}. Whichever provider is active contributes
 * the {@link JwtDecoder} that validates signature, expiry, issuer and audience; the
 * {@link JwtPrincipalConverter} then resolves the validated token to an {@code app_user} row. Both
 * halves of that sentence are provider-shaped; this file is not.
 *
 * <h2>Stateless</h2>
 * Bearer tokens only. No session is created, so there is no session fixation to guard and no
 * cookie for a cross-site request to ride — which is what makes disabling CSRF correct here rather
 * than merely convenient. Form login, HTTP Basic, logout and the saved-request cache are all off:
 * each exists to serve a browser session this application does not have.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    /** Local sign-in. Under Entra no controller answers it and the path simply 404s. */
    private static final String LOGIN_PATH = "/api/auth/login";

    /**
     * Liveness and readiness only. {@code /actuator/info} is exposed but stays authenticated —
     * build and git metadata is free reconnaissance and nothing needs it anonymously.
     */
    private static final String[] HEALTH_PATHS = {"/actuator/health", "/actuator/health/**"};

    /** Phase 4 publishes the contract here; the frontend and CI read it unauthenticated. */
    private static final String[] OPENAPI_PATHS = {
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      JwtDecoder jwtDecoder,
                                                      JwtPrincipalConverter principalConverter,
                                                      JsonAuthenticationEntryPoint entryPoint,
                                                      JsonAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(RequestCacheConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(requests -> requests
                        // The container re-entering the chain for a request it already dispatched
                        // once: an error page, or an async result resuming. Neither can be
                        // originated by a client, and the original REQUEST dispatch was authorized
                        // on the way in. Authorizing them a second time — after the security
                        // context has been cleared — is what turns every 404 into a 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()

                        .requestMatchers(HttpMethod.POST, LOGIN_PATH).permitAll()
                        .requestMatchers(HEALTH_PATHS).permitAll()
                        .requestMatchers(OPENAPI_PATHS).permitAll()

                        // Everything else, including endpoints that do not exist yet.
                        .anyRequest().authenticated())

                // Both handlers are registered twice on purpose. The resource-server filter holds
                // its own entry point for token failures and never consults the shared one, so
                // setting only `exceptionHandling` would leave a bad token answered by Spring's
                // empty-bodied default while a missing token got proper JSON.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(principalConverter)));

        return http.build();
    }
}
