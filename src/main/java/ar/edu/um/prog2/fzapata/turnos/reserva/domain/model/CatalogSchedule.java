package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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

    // Retorna los horarios para esa fecha, en orden. Vacío si está deshabilitado o si la
    // fecha cae en otro día de la semana. No resta ocupaciones ni descarta pasados
    public List<Slot> slotsOn(LocalDate date) {
        // Con duración cero o negativa el avance no progresa: se ignora el horario en vez de colgar el pedido
        if (!enabled || !date.getDayOfWeek().equals(dayOfWeek) || slotDurationMinutes <= 0) {
            return List.of();
        }
        List<Slot> slots = new ArrayList<>();
        LocalTime start = startTime;
        // Avanza de a un slot mientras falte hasta el fin del horario al menos lo que dura uno
        while (start.until(endTime, ChronoUnit.SECONDS) >= slotDurationMinutes * 60L) {
            LocalTime end = start.plusMinutes(slotDurationMinutes);
            slots.add(Slot.builder().date(date).startTime(start).endTime(end).build());
            start = end;
        }
        return slots;
    }
}
