package ar.edu.um.prog2.fzapata.turnos.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        // Clave HMAC en Base64 (variable JWT_SECRET); debe ser el mismo valor con que catálogo firma
        @NotBlank String secret) {
}
