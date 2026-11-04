package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases.CatedraReconciliation.Answer;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases.CatedraReconciliation.Applied;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ExpireOverdueReservationsUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// Sin transacción abierta: la consulta a la cátedra no participa de la de la base, y cada proceso se cierra en la suya
@Component
@RequiredArgsConstructor
public class ExpireOverdueReservationsUseCaseImpl implements ExpireOverdueReservationsUseCase {

    // El mismo motivo que trae el evento AppointmentProcessExpired de la cátedra
    static final String EXPIRATION_REASON = "HOLD_EXPIRED";

    private final ReservationProcessRepository reservationProcessRepository;
    private final CatedraReconciliation catedraReconciliation;
    private final ExpirationPolicy expirationPolicy;
    private final Clock clock;

    private static ExpirationOutcome outcomeOf(Applied applied, ExpirationOutcome whenApplied) {
        return switch (applied) {
            case APPLIED -> whenApplied;
            case UNCHANGED -> ExpirationOutcome.UNCHANGED;
            case CONFLICT -> ExpirationOutcome.CONFLICT;
        };
    }

    private ExpirationOutcome expire(ReservationProcess process) {
        return outcomeOf(catedraReconciliation.apply(process.getId(), ReservationTransition.builder()
                .target(ReservationStatus.EXPIRED).failureReason(EXPIRATION_REASON).build()),
                ExpirationOutcome.EXPIRED);
    }

    private ExpirationOutcome expireOrRepair(ReservationProcess process, Answer answer) {
        return switch (answer.verdict()) {
            case CONFIRMED -> outcomeOf(catedraReconciliation.apply(process.getId(),
                    catedraReconciliation.confirmedBy(answer.appointment())), ExpirationOutcome.CONFIRMED_BY_CATEDRA);
            case NOT_CONFIRMED -> expire(process);
            case INCOMPLETE, CATEDRA_FAILED -> ExpirationOutcome.UNVERIFIED;
        };
    }

    @Override
    public List<ExpirationResult> expireOverdue() {
        List<ReservationProcess> overdue = reservationProcessRepository
                .findExpirable(clock.instant().minus(expirationPolicy.grace()), expirationPolicy.batchSize());
        List<ExpirationResult> results = new ArrayList<>(overdue.size());
        // Tras el primer fallo de la cátedra no se la vuelve a consultar en esta vuelta; los procesos que no
        // necesitan consulta se expiran igual
        boolean catedraFailed = false;
        for (ReservationProcess process : overdue) {
            ExpirationOutcome outcome;
            if (!process.getStatus().phoneMayHaveReachedCatedra()) {
                outcome = expire(process);
            } else if (catedraFailed) {
                outcome = ExpirationOutcome.UNVERIFIED;
            } else {
                Answer answer = catedraReconciliation.askIfConfirmed(process);
                catedraFailed = answer.verdict() == CatedraReconciliation.Verdict.CATEDRA_FAILED;
                outcome = expireOrRepair(process, answer);
            }
            results.add(new ExpirationResult(process.getId(), process.getStatus(), outcome));
        }
        return results;
    }
}
