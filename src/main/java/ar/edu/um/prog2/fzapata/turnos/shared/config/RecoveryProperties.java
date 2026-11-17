package ar.edu.um.prog2.fzapata.turnos.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

// Parámetro de ajuste de la recuperación al arrancar: valor por defecto en application.properties y el entorno gana
@Validated
@ConfigurationProperties(prefix = "recovery")
public record RecoveryProperties(
        // Procesos de cada clase (huérfanos y con teléfono enviado) que toma al arrancar
        @Min(1) int batchSize) {
}
