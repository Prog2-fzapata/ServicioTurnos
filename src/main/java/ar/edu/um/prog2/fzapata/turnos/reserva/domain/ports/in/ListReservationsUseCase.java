package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;

public interface ListReservationsUseCase {

    // Solo las del usuario, de la más reciente a la más antigua por fecha y hora del turno. Sin status quedan afuera
    // las EXPIRED y FAILED (intentos descartados, no son reservas para el usuario); con status se devuelven
    // exactamente las de ese estado, también EXPIRED o FAILED
    PagedResult<ReservationProcess> listReservations(String userId, ReservationStatus status, PageQuery page);
}
