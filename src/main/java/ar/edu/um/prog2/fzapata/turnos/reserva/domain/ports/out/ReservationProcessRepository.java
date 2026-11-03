package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationFilter;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;

public interface ReservationProcessRepository {

    Optional<ReservationProcess> findById(Long id);

    // Vacío si el proceso no existe o es de otro usuario
    Optional<ReservationProcess> findByIdAndUserId(Long id, String userId);

    // Del más reciente al más antiguo por fecha y hora del turno
    PagedResult<ReservationProcess> search(ReservationFilter filter, PageQuery page);

    // Crea (sin id) o actualiza. Al actualizar compara la versión con la de la base; si otro la cambió antes, el
    // adapter lanza ReservationConflictException
    ReservationProcess save(ReservationProcess process);
}
