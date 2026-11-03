package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;

public interface GetReservationUseCase {

    // Vacío si no existe o si es de otro usuario: no se distingue, para no revelar que existe
    Optional<ReservationProcess> getReservation(Long id, String userId);
}
