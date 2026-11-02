package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un horario no disponible según la cátedra. La hora es local de atención, sin zona horaria
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Occupancy {

    private LocalDate date;
    private LocalTime startTime;
    private OccupancyStatus status;
    // Solo tiene valor para HELD
    private Instant expiresAt;
}
