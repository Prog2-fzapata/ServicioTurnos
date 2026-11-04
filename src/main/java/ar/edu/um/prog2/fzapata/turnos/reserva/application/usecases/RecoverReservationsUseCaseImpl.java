package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases.CatedraReconciliation.Answer;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases.CatedraReconciliation.Applied;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases.CatedraReconciliation.Verdict;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.RecoverReservationsUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// Sin transacción abierta: la consulta a la cátedra no participa de la de la base, y cada proceso se cierra en la suya
@Component
@RequiredArgsConstructor
public class RecoverReservationsUseCaseImpl implements RecoverReservationsUseCase {

    private final ReservationProcessRepository reservationProcessRepository;
    private final CatedraReconciliation catedraReconciliation;
    private final RecoveryPolicy recoveryPolicy;

    private static RecoveryOutcome outcomeOf(Applied applied, RecoveryOutcome whenApplied) {
        return switch (applied) {
            case APPLIED -> whenApplied;
            case UNCHANGED -> RecoveryOutcome.UNCHANGED;
            case CONFLICT -> RecoveryOutcome.CONFLICT;
        };
    }

    // El pedido del hold quedó sin resultado conocido (se cayó el servicio o se perdió la respuesta): nunca se
    // recibieron los ids, así que no se puede confirmar ni reconciliar. El hold que la cátedra haya creado vence solo
    private RecoveryOutcome failOrphan(ReservationProcess process) {
        return outcomeOf(catedraReconciliation.apply(process.getId(), ReservationTransition.builder()
                .target(ReservationStatus.FAILED).failureReason(StartReservationUseCaseImpl.HOLD_RESULT_UNKNOWN)
                .build()), RecoveryOutcome.ORPHAN_FAILED);
    }

    // Solo se actúa con evidencia de que la cátedra lo tiene confirmado; si no, se deja como está
    private RecoveryOutcome repairIfConfirmed(ReservationProcess process, Answer answer) {
        return switch (answer.verdict()) {
            case CONFIRMED -> outcomeOf(catedraReconciliation.apply(process.getId(),
                    catedraReconciliation.confirmedBy(answer.appointment())), RecoveryOutcome.CONFIRMED_BY_CATEDRA);
            case NOT_CONFIRMED -> RecoveryOutcome.STILL_OPEN;
            case INCOMPLETE, CATEDRA_FAILED -> RecoveryOutcome.UNVERIFIED;
        };
    }

    @Override
    public List<RecoveryResult> recover(Instant instanceStartedAt) {
        List<RecoveryResult> results = new ArrayList<>();
        for (ReservationProcess process : reservationProcessRepository.findStaleHoldRequests(instanceStartedAt,
                recoveryPolicy.batchSize())) {
            results.add(new RecoveryResult(process.getId(), process.getStatus(), failOrphan(process)));
        }
        // Tras el primer fallo de la cátedra no se la vuelve a consultar
        boolean catedraFailed = false;
        for (ReservationProcess process : reservationProcessRepository.findWithPhoneSent(recoveryPolicy.batchSize())) {
            RecoveryOutcome outcome;
            if (catedraFailed) {
                outcome = RecoveryOutcome.UNVERIFIED;
            } else {
                Answer answer = catedraReconciliation.askIfConfirmed(process);
                catedraFailed = answer.verdict() == Verdict.CATEDRA_FAILED;
                outcome = repairIfConfirmed(process, answer);
            }
            results.add(new RecoveryResult(process.getId(), process.getStatus(), outcome));
        }
        return results;
    }
}
