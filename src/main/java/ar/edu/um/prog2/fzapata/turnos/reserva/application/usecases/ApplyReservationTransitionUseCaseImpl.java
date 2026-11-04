package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationChangePublisher;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ApplyReservationTransitionUseCaseImpl implements ApplyReservationTransitionUseCase {

    private final ReservationProcessRepository reservationProcessRepository;
    private final ReservationChangePublisher reservationChangePublisher;
    private final ConflictRetryPolicy conflictRetryPolicy;
    private final Clock clock;

    private static <T> boolean copy(T incoming, T current, Consumer<T> setter, boolean onlyMissing) {
        if (incoming == null || (onlyMissing && current != null)) {
            return false;
        }
        setter.accept(incoming);
        return true;
    }

    // Solo se copia lo que vino: un null no borra el teléfono, el vencimiento ni los ids que el proceso ya tenía.
    // Con onlyMissing no se pisa nada que el proceso ya tenga. Devuelve si copió algo
    private boolean copyData(ReservationProcess process, ReservationTransition transition, boolean onlyMissing) {
        boolean copied = copy(transition.holdId(), process.getHoldId(), process::setHoldId, onlyMissing);
        copied |= copy(transition.reservationProcessId(), process.getReservationProcessId(),
                process::setReservationProcessId, onlyMissing);
        copied |= copy(transition.expiresAt(), process.getExpiresAt(), process::setExpiresAt, onlyMissing);
        copied |= copy(transition.patientPhone(), process.getPatientPhone(), process::setPatientPhone, onlyMissing);
        copied |= copy(transition.requestEventId(), process.getRequestEventId(), process::setRequestEventId,
                onlyMissing);
        copied |= copy(transition.reservationId(), process.getReservationId(), process::setReservationId,
                onlyMissing);
        copied |= copy(transition.failureReason(), process.getFailureReason(), process::setFailureReason,
                onlyMissing);
        copied |= copy(transition.cancellationReason(), process.getCancellationReason(),
                process::setCancellationReason, onlyMissing);
        return copied;
    }

    // Un proceso final es inmutable: ni siquiera se le completan datos
    private TransitionResult sameState(ReservationProcess process, ReservationTransition transition) {
        ReservationStatus status = process.getStatus();
        if (status.isFinal() || !copyData(process, transition, true)) {
            return new TransitionResult(TransitionOutcome.ALREADY_IN_STATE, status, status, process);
        }
        process.setUpdatedAt(clock.instant());
        ReservationProcess saved = reservationProcessRepository.save(process);
        // El estado no cambia pero sí lo que ve la app (por ejemplo canSubmitPhone con el pedido de teléfono)
        reservationChangePublisher.publish(saved);
        return new TransitionResult(TransitionOutcome.DATA_COMPLETED, status, status, saved);
    }

    // Un intento sobre el proceso recién leído. Solo guarda si el paso se aplica, y el guardado compara la versión
    private TransitionResult attempt(ReservationProcess process, ReservationTransition transition) {
        ReservationStatus from = process.getStatus();
        ReservationStatus target = transition.target();
        // Antes que la tabla: repetir el paso que ya se dio (un evento duplicado, una cancelación repetida) no es un error.
        // Si el proceso no es final y le faltan datos de los que trae el paso, se completan sin pisar los que tiene
        if (from == target) {
            return sameState(process, transition);
        }
        if (!from.canTransitionTo(target)) {
            return new TransitionResult(TransitionOutcome.REJECTED, from, target, process);
        }
        Instant now = clock.instant();
        process.setStatus(target);
        copyData(process, transition, false);
        process.setUpdatedAt(now);
        if (target == ReservationStatus.CANCELLED) {
            process.setCancelledAt(now);
        }
        ReservationProcess saved = reservationProcessRepository.save(process);
        reservationChangePublisher.publish(saved);
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

    @Override
    public Optional<TransitionResult> applyTransitionOnce(Long processId, ReservationTransition transition) {
        return reservationProcessRepository.findById(processId).map(process -> attempt(process, transition));
    }
}
