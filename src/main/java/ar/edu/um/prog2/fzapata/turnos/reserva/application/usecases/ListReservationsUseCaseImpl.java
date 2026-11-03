package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationFilter;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ListReservationsUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ListReservationsUseCaseImpl implements ListReservationsUseCase {

    private final ReservationProcessRepository reservationProcessRepository;

    @Override
    public PagedResult<ReservationProcess> listReservations(String userId, ReservationStatus status, PageQuery page) {
        // Sin estado pedido se ocultan los intentos descartados; pedir su estado los trae
        return reservationProcessRepository.search(new ReservationFilter(userId, status, status == null), page);
    }
}
