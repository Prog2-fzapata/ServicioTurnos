package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GetReservationUseCaseImpl implements GetReservationUseCase {

    private final ReservationProcessRepository reservationProcessRepository;

    @Override
    public Optional<ReservationProcess> getReservation(Long id, String userId) {
        return reservationProcessRepository.findByIdAndUserId(id, userId);
    }
}
