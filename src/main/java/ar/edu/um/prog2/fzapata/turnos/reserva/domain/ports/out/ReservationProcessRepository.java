package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationFilter;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;

public interface ReservationProcessRepository {

    Optional<ReservationProcess> findById(Long id);

    // Por el id que la cátedra le dio al proceso (reservationProcessId), que es único
    Optional<ReservationProcess> findByReservationProcessId(String reservationProcessId);

    // Vacío si el proceso no existe o es de otro usuario
    Optional<ReservationProcess> findByIdAndUserId(Long id, String userId);

    // Del más reciente al más antiguo por fecha y hora del turno
    PagedResult<ReservationProcess> search(ReservationFilter filter, PageQuery page);

    // Procesos abiertos con vencimiento (ReservationStatus.EXPIRABLE) cuyo expiresAt es igual o anterior a expiredUntil,
    // del vencimiento más viejo al más nuevo, hasta limit
    List<ReservationProcess> findExpirable(Instant expiredUntil, int limit);

    // Crea (sin id) o actualiza. Al actualizar compara la versión con la de la base; si otro la cambió antes, el
    // adapter lanza ReservationConflictException
    ReservationProcess save(ReservationProcess process);
}
