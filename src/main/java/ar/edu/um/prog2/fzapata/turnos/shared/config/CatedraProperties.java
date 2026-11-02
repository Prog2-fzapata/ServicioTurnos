package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Conexión a la API REST de la cátedra. La URL y el JWT técnico son obligatorios y vienen del entorno; el resto son
// parámetros de ajuste con valor por defecto en application.properties, y la variable de entorno gana si está definida
@Validated
@ConfigurationProperties(prefix = "catedra")
public record CatedraProperties(
        // URL base de la API de la cátedra, sin "/" final (variable CATEDRA_BASE_URL)
        @NotBlank String baseUrl,
        // JWT técnico (variable CATEDRA_JWT): nunca se escribe en el código, en logs ni en mensajes de error
        @NotBlank String jwt,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        // Intentos en total, el primero incluido: 1 desactiva los reintentos
        @Min(1) int maxAttempts,
        // Espera antes del primer reintento; cada uno espera el doble del anterior
        @NotNull Duration initialBackoff) {

    // El toString de un record imprime todos los campos: se oculta el JWT para que no llegue a ningún log
    @Override
    public String toString() {
        return "CatedraProperties[baseUrl=" + baseUrl + ", jwt=****, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", maxAttempts=" + maxAttempts + ", initialBackoff="
                + initialBackoff + "]";
    }
}
