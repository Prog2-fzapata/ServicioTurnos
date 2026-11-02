package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;

public interface ReservationProcessRepository {

    Optional<ReservationProcess> findById(Long id);

    // Crea (sin id) o actualiza. Al actualizar compara la versión con la de la base; si otro la cambió antes, el
    // adapter lanza ReservationConflictException
    ReservationProcess save(ReservationProcess process);
}
