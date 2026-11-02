package ar.edu.um.prog2.fzapata.turnos.reserva.application.service;

import org.springframework.stereotype.Service;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetAvailabilityUseCase;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final GetAvailabilityUseCase getAvailabilityUseCase;

    public Availability getAvailability(AvailabilityQuery query) {
        return getAvailabilityUseCase.getAvailability(query)
                .orElseThrow(() -> new ProfessionalNotFoundException(query.professionalId()));
    }
}
