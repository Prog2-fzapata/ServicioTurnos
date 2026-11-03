package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// El topic de eventos de reserva (obligatorio, sin valor por defecto) y los reintentos del consumidor. La conexión y el
// grupo usan las propiedades estándar spring.kafka.*
@Validated
@ConfigurationProperties(prefix = "catedra.kafka")
public record CatedraKafkaProperties(
        @NotBlank String appointmentActionsTopic,
        // Reintentos después del primer intento; 0 desactiva los reintentos
        @Min(0) int maxRetries,
        // Espera antes del primer reintento; cada reintento espera el doble del anterior
        @NotNull Duration initialBackoff) {
}
