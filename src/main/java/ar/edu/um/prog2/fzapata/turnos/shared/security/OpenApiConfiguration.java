package ar.edu.um.prog2.fzapata.turnos.shared.security;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfiguration {

    static final String BEARER_SCHEME = "bearer-jwt";

    // Declara el esquema Bearer JWT (Swagger UI muestra el botón «Authorize») y lo exige en toda la API:
    // el token se obtiene en catálogo (POST /api/authenticate) y se pega acá
    @Bean
    OpenApiCustomizer bearerJwtCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSecuritySchemes(BEARER_SCHEME,
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
            openApi.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
        };
    }
}
