package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ApplyReservationTransitionUseCaseImpl implements ApplyReservationTransitionUseCase {

    private final ReservationProcessRepository reservationProcessRepository;
    private final ConflictRetryPolicy conflictRetryPolicy;
    private final Clock clock;

    // Solo se copia lo que vino: un null no borra el teléfono, el vencimiento ni los ids que el proceso ya tenía
    private void copyData(ReservationProcess process, ReservationTransition transition) {
        if (transition.holdId() != null) {
            process.setHoldId(transition.holdId());
        }
        if (transition.reservationProcessId() != null) {
            process.setReservationProcessId(transition.reservationProcessId());
        }
        if (transition.expiresAt() != null) {
            process.setExpiresAt(transition.expiresAt());
        }
        if (transition.patientPhone() != null) {
            process.setPatientPhone(transition.patientPhone());
        }
        if (transition.requestEventId() != null) {
            process.setRequestEventId(transition.requestEventId());
        }
        if (transition.reservationId() != null) {
            process.setReservationId(transition.reservationId());
        }
        if (transition.failureReason() != null) {
            process.setFailureReason(transition.failureReason());
        }
        if (transition.cancellationReason() != null) {
            process.setCancellationReason(transition.cancellationReason());
        }
    }

    // Un intento sobre el proceso recién leído. Solo guarda si el paso se aplica, y el guardado compara la versión
    private TransitionResult attempt(ReservationProcess process, ReservationTransition transition) {
        ReservationStatus from = process.getStatus();
        ReservationStatus target = transition.target();
        // Antes que la tabla: repetir el paso que ya se dio (un evento duplicado, una cancelación repetida) no es un error
        if (from == target) {
            return new TransitionResult(TransitionOutcome.ALREADY_IN_STATE, from, target, process);
        }
        if (!from.canTransitionTo(target)) {
            return new TransitionResult(TransitionOutcome.REJECTED, from, target, process);
        }
        Instant now = clock.instant();
        process.setStatus(target);
        copyData(process, transition);
        process.setUpdatedAt(now);
        if (target == ReservationStatus.CANCELLED) {
            process.setCancelledAt(now);
        }
        ReservationProcess saved = reservationProcessRepository.save(process);
        return new TransitionResult(TransitionOutcome.APPLIED, from, target, saved);
    }

    // Cada intento relee el proceso: si otro lo cambió, se decide de nuevo sobre el estado vigente (un paso que ya no
    // corresponde pasa a ser REJECTED o ALREADY_IN_STATE). Es seguro repetir porque los estados solo avanzan
    @Override
    public Optional<TransitionResult> applyTransition(Long processId, ReservationTransition transition) {
        for (int attemptNumber = 1;; attemptNumber++) {
            Optional<ReservationProcess> found = reservationProcessRepository.findById(processId);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(attempt(found.get(), transition));
            } catch (ReservationConflictException e) {
                if (attemptNumber >= conflictRetryPolicy.maxAttempts()) {
                    throw e;
                }
            }
        }
    }
}
