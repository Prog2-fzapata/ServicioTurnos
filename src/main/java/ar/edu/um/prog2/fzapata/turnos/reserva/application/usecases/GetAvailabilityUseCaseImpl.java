package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AgendaSlots;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogProfessional;
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
    private final ProfessionalGuard professionalGuard;
    // Lleva la zona de atención: "ahora" se mide en la hora local en que la cátedra expresa los horarios
    private final Clock clock;

    @Override
    public Optional<Availability> getAvailability(AvailabilityQuery query) {
        Optional<CatalogProfessional> found = catalogClient.findProfessional(query.professionalId());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        CatalogProfessional professional = found.get();
        professionalGuard.requireEnabled(professional);

        Map<LocalDateTime, Slot> slots = AgendaSlots.generate(professional, query.from(), query.to());
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
