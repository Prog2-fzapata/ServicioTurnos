package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un horario semanal de la agenda vigente que informa catálogo. Las horas son locales de atención, sin zona horaria
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSchedule {

    private Long id;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private int slotDurationMinutes;
    private boolean enabled;
}
