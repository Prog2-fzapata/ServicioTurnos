package ar.edu.um.prog2.fzapata.turnos.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

// Parámetro de ajuste: valor por defecto en application.properties y la variable de entorno gana si está definida
@Validated
@ConfigurationProperties(prefix = "reservation")
public record ReservationProperties(
        // Intentos en total, el primero incluido, cuando dos actualizaciones del mismo proceso chocan por la versión:
        // 1 desactiva el reintento
        @Min(1) int conflictMaxAttempts) {
}
