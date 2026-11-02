package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;

public interface GetAvailabilityUseCase {

    // Vacío si el profesional no existe. Un profesional (o categoría) deshabilitado es una excepción de negocio
    Optional<Availability> getAvailability(AvailabilityQuery query);
}
