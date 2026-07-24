package com.deskdibs.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The document metadata and bearer-auth scheme behind {@code /v3/api-docs} and
 * {@code /swagger-ui.html}.
 *
 * <p>Both paths are already permitted unauthenticated in {@link com.deskdibs.auth.SecurityConfig}
 * — the contract has to be readable before anyone holds a token to call the API it describes.
 *
 * <p>{@code backend/openapi.json} is a point-in-time export of what this bean generates, committed
 * so the frontend can mock against a frozen contract instead of a moving target. See the repository
 * README for the one-line command that regenerates it.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI deskDibsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DeskDibs API")
                        .version("v1")
                        .description("""
                                Office hot-desk seat booking. The floor map at GET /api/seatmap is the \
                                endpoint the rest of the product hangs off; every mutation broadcasts a \
                                SeatStatusChanged message over STOMP to /topic/seatmap/{date} once its \
                                transaction commits.""")
                        .contact(new Contact().name("DeskDibs")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        Obtain one from POST /api/auth/login (local provider) or the \
                                        Entra OIDC flow, then send it as "Authorization: Bearer <token>".""")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
