package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogSchedule;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Occupancy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Slot;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetAvailabilityUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatalogClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GetAvailabilityUseCaseImpl implements GetAvailabilityUseCase {

    private final CatalogClient catalogClient;
    private final CatedraApiClient catedraApiClient;
    // Lleva la zona de atención: "ahora" se mide en la hora local en que la cátedra expresa los horarios
    private final Clock clock;

    // Una categoría apagada bloquea a sus profesionales: cada profesional tiene una sola
    private void requireEnabled(CatalogProfessional professional) {
        if (!professional.isEnabled()) {
            throw new ProfessionalDisabledException("El profesional " + professional.getId() + " está deshabilitado");
        }
        if (!professional.isCategoryEnabled()) {
            throw new ProfessionalDisabledException(
                    "La categoría del profesional " + professional.getId() + " está deshabilitada");
        }
    }

    // Parte de startTime y avanza de a slotDurationMinutes mientras el slot termine en o antes de endTime
    private void addSlots(Map<LocalDateTime, Slot> slots, LocalDate date, CatalogSchedule schedule) {
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

    private Map<LocalDateTime, Slot> generateSlots(CatalogProfessional professional, AvailabilityQuery query) {
        // El mapa ordenado deja los slots por fecha y hora sin un sort aparte
        Map<LocalDateTime, Slot> slots = new TreeMap<>();
        for (LocalDate date = query.from(); !date.isAfter(query.to()); date = date.plusDays(1)) {
            for (CatalogSchedule schedule : professional.getWeeklySchedules()) {
                if (schedule.isEnabled() && schedule.getDayOfWeek() == date.getDayOfWeek()) {
                    addSlots(slots, date, schedule);
                }
            }
        }
        return slots;
    }

    @Override
    public Optional<Availability> getAvailability(AvailabilityQuery query) {
        Optional<CatalogProfessional> found = catalogClient.findProfessional(query.professionalId());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        CatalogProfessional professional = found.get();
        requireEnabled(professional);

        Map<LocalDateTime, Slot> slots = generateSlots(professional, query);
        // CONFIRMED y también HELD: un hold puede vencer, pero ofrecerlo terminaría en un 409 al reservar
        List<Occupancy> occupancies = catedraApiClient.findOccupancies(query.professionalId(), query.from(),
                query.to());
        occupancies.forEach(occupancy -> slots.remove(LocalDateTime.of(occupancy.getDate(), occupancy.getStartTime())));
        LocalDateTime now = LocalDateTime.now(clock);
        slots.keySet().removeIf(start -> start.isBefore(now));

        return Optional.of(Availability.builder()
                .professionalId(query.professionalId())
                .from(query.from())
                .to(query.to())
                .slots(List.copyOf(slots.values()))
                .build());
    }
}
