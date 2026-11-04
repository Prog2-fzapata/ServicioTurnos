package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Parámetros de ajuste de la tarea de vencimiento: valor por defecto en application.properties y el entorno gana
@Validated
@ConfigurationProperties(prefix = "expiration")
public record ExpirationProperties(
        // Espera entre el fin de una vuelta y el comienzo de la siguiente
        @NotNull @DurationMin(nanos = 1) Duration interval,
        // Cuánto después de su expiresAt se expira un proceso: deja llegar el evento de la cátedra que viene en camino.
        // Cero lo expira en cuanto vence
        @NotNull @DurationMin(nanos = 0) Duration grace,
        // Procesos que toma por vuelta; lo que sobre sigue en la vuelta siguiente
        @Min(1) int batchSize) {
}
