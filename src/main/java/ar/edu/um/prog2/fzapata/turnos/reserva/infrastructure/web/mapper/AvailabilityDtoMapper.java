package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Slot;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.AvailabilityResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.SlotResponse;

@Component
public class AvailabilityDtoMapper {

    private SlotResponse toResponse(Slot slot) {
        return SlotResponse.builder()
                .date(slot.getDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .build();
    }

    public AvailabilityResponse toResponse(Availability domain) {
        if (domain == null) return null;
        return AvailabilityResponse.builder()
                .professionalId(domain.getProfessionalId())
                .from(domain.getFrom())
                .to(domain.getTo())
                .slots(domain.getSlots().stream().map(this::toResponse).toList())
                .build();
    }
}
