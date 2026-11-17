package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OccupancyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un campo nuevo que agregue la cátedra no debe romper a turnos
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OccupancyResponse {
    private LocalDate date;
    private LocalTime startTime;
    private OccupancyStatus status;
    private Instant expiresAt;
}
