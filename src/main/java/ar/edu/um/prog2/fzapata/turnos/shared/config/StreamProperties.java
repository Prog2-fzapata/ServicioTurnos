package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Parámetros de ajuste del stream de cambios de reserva: valor por defecto en application.properties y el entorno gana
@Validated
@ConfigurationProperties(prefix = "stream")
public record StreamProperties(
        // Cada cuánto se manda el comentario de latido; también es la precisión con que se cierra una conexión cuyo
        // JWT venció
        @NotNull @DurationMin(nanos = 1) Duration heartbeatInterval,
        // Conexiones abiertas a la vez por usuario: al superarlo se cierra la más vieja
        @Min(1) int maxConnectionsPerUser) {
}
