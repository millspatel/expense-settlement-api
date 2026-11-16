package com.splitwise.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the JWT bearer scheme so Swagger UI shows an "Authorize" button, and applies it to every
 * endpoint by default. Public endpoints (signup/login) opt out with an empty {@code @SecurityRequirements}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Splitwise API",
                version = "1.0.0",
                description = "Expense splitting with JWT auth and debt simplification. "
                        + "Log in via /api/auth/login, then click Authorize and paste the token."),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
