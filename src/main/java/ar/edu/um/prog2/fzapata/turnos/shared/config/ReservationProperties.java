package ar.edu.um.prog2.fzapata.turnos.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// Parámetros de ajuste: valor por defecto en application.properties y la variable de entorno gana si está definida
@Validated
@ConfigurationProperties(prefix = "reservation")
public record ReservationProperties(
        // Intentos en total, el primero incluido, cuando dos actualizaciones del mismo proceso chocan por la versión:
        // 1 desactiva el reintento. Tiene techo (RetryLimits)
        @Min(1) @Max(RetryLimits.MAX_ATTEMPTS) int conflictMaxAttempts,
        // Intentos en total, el primero incluido, para conseguir un hold confirmado: cada intento es un hold más su
        // confirmación. Se repite si se perdió la respuesta del hold y el horario figura libre, o si el hold venció antes
        // de confirmarse. 1 desactiva el reintento. Tiene techo (RetryLimits)
        @Min(1) @Max(RetryLimits.MAX_HOLD_ATTEMPTS) int holdMaxAttempts) {
}
