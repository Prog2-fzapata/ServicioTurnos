package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Los topics y el groupId de la cuenta (obligatorios, sin valor por defecto) y los reintentos del consumidor. La
// conexión y el consumer group usan las propiedades estándar spring.kafka.*
@Validated
@ConfigurationProperties(prefix = "catedra.kafka")
public record CatedraKafkaProperties(
        @NotBlank String appointmentActionsTopic,
        // Topic al que se publica el teléfono, y el groupId de la cuenta, que el mensaje lleva como campo
        @NotBlank String appointmentPhoneTopic,
        @NotBlank String accountGroupId,
        // Reintentos después del primer intento; 0 desactiva los reintentos. Tiene techo (RetryLimits)
        @Min(0) @Max(RetryLimits.MAX_ATTEMPTS) int maxRetries,
        // Espera antes del primer reintento; cada reintento espera el doble del anterior
        @NotNull Duration initialBackoff) {
}
