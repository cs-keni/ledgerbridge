package com.ledgerbridge.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LedgerBridge API")
                        .description("""
                                Real-time banking transaction and risk monitoring API.

                                Use the **Authorize** button to supply a JWT Bearer token.
                                Obtain a token via `POST /api/auth/login`, or use the
                                demo credentials from the README with the live demo URL.
                                """)
                        .version("1.0")
                        .contact(new Contact()
                                .name("Kenny Nguyen")
                                .url("https://github.com/cs-keni/ledgerbridge")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token — 15 min TTL. Refresh via POST /api/auth/refresh.")));
    }
}
