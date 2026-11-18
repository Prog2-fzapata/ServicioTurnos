package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

// Los horarios que salen de la agenda semanal de un profesional, sin restar ocupaciones ni descartar pasados. Lo usan
// la disponibilidad y el inicio de una reserva, para que las dos apliquen exactamente la misma regla
public final class AgendaSlots {

    private AgendaSlots() {
    }

    // Retorna los horarios de ese día. Ordenados por fecha y hora de inicio, ambas fechas inclusivas
    public static NavigableMap<LocalDateTime, Slot> generate(CatalogProfessional professional, LocalDate from,
            LocalDate to) {
        // NavigableMap tiene métodos para consultar por posición de orden, treemap, map ordenado sin duplicados
        NavigableMap<LocalDateTime, Slot> slots = new TreeMap<>();
        // Desde from hasta que !date.isAfter(to) sea falso.
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            // Añade los slots enabled de esas fechas al map
            for (CatalogSchedule schedule : professional.getWeeklySchedules()) {
                if (schedule.isEnabled() && date.getDayOfWeek().equals(schedule.getDayOfWeek())) {
                    addSlots(slots, date, schedule);
                }
            }
        }
        return slots;
    }

    // Parte de startTime y avanza de a slotDurationMinutes mientras el slot termine en o antes de endTime
    private static void addSlots(Map<LocalDateTime, Slot> slots, LocalDate date, CatalogSchedule schedule) {
        long duration = schedule.getSlotDurationMinutes();
        // Con duración cero o negativa el avance no progresa: se ignora el horario en vez de colgar el pedido
        if (duration <= 0) {
            return;
        }
        LocalTime start = schedule.getStartTime();

        // Recorre el tiempo mientras falte hasta el fin del horario al menos lo que dura un slot
        // agregando los slots mientras no se repitan por fecha y start
        while (start.until(schedule.getEndTime(), ChronoUnit.SECONDS) >= duration * 60) {
            LocalTime end = start.plusMinutes(duration);
            // Dos horarios que se superponen pueden repetir un inicio: no se agrega el repetido por fecha y start
            slots.putIfAbsent(LocalDateTime.of(date, start), Slot.builder().date(date).startTime(start).endTime(end).build());
            start = end;
        }
    }
}
