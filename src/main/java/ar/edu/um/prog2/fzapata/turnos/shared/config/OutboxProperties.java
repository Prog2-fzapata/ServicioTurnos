package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Parámetros de ajuste del relay del outbox: valor por defecto en application.properties y el entorno gana
@Validated
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(
        // Espera entre el fin de una vuelta del relay y el comienzo de la siguiente
        @NotNull @DurationMin(nanos = 1) Duration relayInterval,
        // Mensajes que toma por vuelta
        @Min(1) int relayBatchSize,
        // Espera máxima por la confirmación de Kafka de un mensaje
        @NotNull @DurationMin(nanos = 1) Duration publishTimeout) {
}
