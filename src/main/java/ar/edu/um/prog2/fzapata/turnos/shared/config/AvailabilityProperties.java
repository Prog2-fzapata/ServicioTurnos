package ar.edu.um.prog2.fzapata.turnos.shared.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Parámetros de ajuste de la disponibilidad: tienen valor por defecto en application.properties y la variable de
// entorno gana si está definida
@Validated
@ConfigurationProperties(prefix = "availability")
public record AvailabilityProperties(
        // Zona en que la cátedra expresa los horarios (hora local de atención): decide qué slots ya son pasados
        @NotNull ZoneId zone,
        // Días máximos entre from y to, ambos incluidos
        @Min(1) int maxRangeDays) {
}
