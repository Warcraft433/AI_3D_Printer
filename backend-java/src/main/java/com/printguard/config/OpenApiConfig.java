package com.printguard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 / Swagger Interactive API documentation configuration
 * with JWT Bearer authentication scheme.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("PrintGuard AI — Enterprise 3D Printing Operating System API")
                        .description("Interactive REST and WebSocket API documentation for procedural CAD model generation, " +
                                "PrusaSlicer G-code slicing, YOLOv8 defect detection, and IoT printer hardware control.")
                        .version("2.4.0")
                        .contact(new Contact()
                                .name("PrintGuard AI Engineering Team")
                                .url("https://github.com/Warcraft433/AI_3D_Printer")
                                .email("dev@printguard.local"))
                        .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT bearer access token acquired from /api/auth/login")));
    }
}
