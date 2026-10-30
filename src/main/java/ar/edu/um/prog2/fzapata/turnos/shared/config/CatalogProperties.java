package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Conexión al servicio de catálogo. La URL es de despliegue (variable CATALOGO_BASE_URL, obligatoria); el resto son
// parámetros de ajuste con valor por defecto en application.properties, y la variable de entorno gana si está definida
@Validated
@ConfigurationProperties(prefix = "catalogo")
public record CatalogProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        // Intentos en total, el primero incluido: 1 desactiva los reintentos
        @Min(1) int maxAttempts,
        // Espera antes del primer reintento; cada uno espera el doble del anterior
        @NotNull Duration initialBackoff) {
}
