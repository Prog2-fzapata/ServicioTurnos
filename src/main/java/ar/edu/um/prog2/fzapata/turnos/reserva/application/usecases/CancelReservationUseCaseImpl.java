package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationNotCancellableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.CancelReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// Sin transacción abierta mientras llama a la cátedra. Si la cátedra cancela y el guardado local falla, el proceso
// sigue CONFIRMED y repetir el pedido lo arregla: cancelar en la cátedra es idempotente
@Component
@RequiredArgsConstructor
public class CancelReservationUseCaseImpl implements CancelReservationUseCase {

    private final ReservationProcessRepository reservationProcessRepository;
    private final CatedraApiClient catedraApiClient;
    private final ApplyReservationTransitionUseCase applyReservationTransition;

    private void cancelInCatedra(ReservationProcess process, String reason) {
        try {
            catedraApiClient.cancelAppointment(process.getReservationProcessId(), reason);
        } catch (CatedraRejectedException e) {
            if (e.getRejection() == CatedraRejection.RESERVATION_NOT_CANCELLABLE) {
                throw new ReservationNotCancellableException(process.getId());
            }
            // Que no la conozca o la crea de otro es un desajuste entre los servicios, no un error del usuario
            throw new CatedraResponseException("La cátedra rechazó la cancelación con " + e.getRejection(), e);
        }
    }

    private ReservationProcess markAsCancelled(ReservationProcess process, String reason) {
        TransitionResult result = applyReservationTransition.applyTransition(process.getId(),
                ReservationTransition.builder().target(ReservationStatus.CANCELLED).cancellationReason(reason).build())
                .orElseThrow(() -> new ReservationProcessNotFoundException(process.getId()));
        // ALREADY_IN_STATE: otra cancelación del mismo proceso se adelantó, y el resultado es el mismo
        if (result.outcome() == TransitionOutcome.REJECTED) {
            throw new ReservationNotCancellableException(process.getId(), result.from());
        }
        return result.process();
    }

    @Override
    public Optional<ReservationProcess> cancelReservation(Long id, String userId, String reason) {
        Optional<ReservationProcess> found = reservationProcessRepository.findByIdAndUserId(id, userId);
        if (found.isEmpty()) {
            return found;
        }
        ReservationProcess process = found.get();
        if (process.getStatus() == ReservationStatus.CANCELLED) {
            return found;
        }
        if (process.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ReservationNotCancellableException(id, process.getStatus());
        }
        cancelInCatedra(process, reason);
        return Optional.of(markAsCancelled(process, reason));
    }
}
