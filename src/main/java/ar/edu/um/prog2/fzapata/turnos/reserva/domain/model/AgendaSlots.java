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

    // Parte de startTime y avanza de a slotDurationMinutes mientras el slot termine en o antes de endTime
    private static void addSlots(Map<LocalDateTime, Slot> slots, LocalDate date, CatalogSchedule schedule) {
        long duration = schedule.getSlotDurationMinutes();
        // Con duración cero o negativa el avance no progresa: se ignora el horario en vez de colgar el pedido
        if (duration <= 0) {
            return;
        }
        LocalTime start = schedule.getStartTime();
        while (start.until(schedule.getEndTime(), ChronoUnit.SECONDS) >= duration * 60) {
            LocalTime end = start.plusMinutes(duration);
            // Dos horarios que se superponen pueden repetir un inicio: se deduplica por fecha e inicio
            slots.putIfAbsent(LocalDateTime.of(date, start),
                    Slot.builder().date(date).startTime(start).endTime(end).build());
            start = end;
        }
    }

    // Ordenados por fecha y hora de inicio, ambas fechas inclusivas
    public static NavigableMap<LocalDateTime, Slot> generate(CatalogProfessional professional, LocalDate from,
            LocalDate to) {
        NavigableMap<LocalDateTime, Slot> slots = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (CatalogSchedule schedule : professional.getWeeklySchedules()) {
                if (schedule.isEnabled() && schedule.getDayOfWeek() == date.getDayOfWeek()) {
                    addSlots(slots, date, schedule);
                }
            }
        }
        return slots;
    }
}
