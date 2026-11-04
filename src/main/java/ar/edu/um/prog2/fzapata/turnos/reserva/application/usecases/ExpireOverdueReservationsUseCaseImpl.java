package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraAuthenticationException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentPage;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraAppointment;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ExpireOverdueReservationsUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;

// Sin transacción abierta: la consulta a la cátedra no participa de la de la base, y cada proceso se cierra en la suya
@Component
@RequiredArgsConstructor
public class ExpireOverdueReservationsUseCaseImpl implements ExpireOverdueReservationsUseCase {

    // El mismo motivo que trae el evento AppointmentProcessExpired de la cátedra
    static final String EXPIRATION_REASON = "HOLD_EXPIRED";
    // Un paciente tiene muy pocos turnos confirmados en un mismo día: una página alcanza. Si no alcanzara no se expira
    private static final int CONFIRMED_PAGE_SIZE = 100;

    private final ReservationProcessRepository reservationProcessRepository;
    private final ApplyReservationTransitionUseCase applyReservationTransition;
    private final CatedraApiClient catedraApiClient;
    private final ExpirationPolicy expirationPolicy;
    private final Clock clock;

    // La respuesta de la cátedra a «¿tiene este proceso confirmado?». complete es falso si hay más resultados que los
    // de la página y el proceso no estaba entre ellos: no se puede afirmar que no esté
    private record Confirmation(Optional<CatedraAppointment> appointment, boolean complete) {
    }

    private Confirmation askCatedra(ReservationProcess process) {
        LocalDate date = process.getAppointmentDate();
        AppointmentPage page = catedraApiClient.findAppointments(
                new AppointmentQuery(process.getUserId(), AppointmentStatus.CONFIRMED, date, date), 0,
                CONFIRMED_PAGE_SIZE);
        Optional<CatedraAppointment> match = page.content().stream()
                .filter(appointment -> process.getReservationProcessId() != null
                        && process.getReservationProcessId().equals(appointment.getReservationProcessId()))
                .findFirst();
        return new Confirmation(match, page.totalElements() <= page.content().size());
    }

    private ExpirationOutcome apply(ReservationProcess process, ReservationTransition transition,
            ExpirationOutcome whenApplied) {
        try {
            boolean applied = applyReservationTransition.applyTransition(process.getId(), transition)
                    .filter(result -> result.outcome() == TransitionOutcome.APPLIED).isPresent();
            return applied ? whenApplied : ExpirationOutcome.UNCHANGED;
        } catch (ReservationConflictException e) {
            return ExpirationOutcome.CONFLICT;
        }
    }

    private ExpirationOutcome expire(ReservationProcess process) {
        return apply(process, ReservationTransition.builder().target(ReservationStatus.EXPIRED)
                .failureReason(EXPIRATION_REASON).build(), ExpirationOutcome.EXPIRED);
    }

    private ExpirationOutcome repairAsConfirmed(ReservationProcess process, CatedraAppointment appointment) {
        return apply(process, ReservationTransition.builder().target(ReservationStatus.CONFIRMED)
                .reservationId(appointment.getReservationId()).build(), ExpirationOutcome.CONFIRMED_BY_CATEDRA);
    }

    // Ante la duda no se expira: un EXPIRED es final y descartaría una confirmación que la cátedra sí tiene
    private ExpirationOutcome expireOrReconcile(ReservationProcess process, Confirmation confirmation) {
        if (confirmation.appointment().isPresent()) {
            return repairAsConfirmed(process, confirmation.appointment().get());
        }
        return confirmation.complete() ? expire(process) : ExpirationOutcome.UNVERIFIED;
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
                try {
                    outcome = expireOrReconcile(process, askCatedra(process));
                } catch (CatedraUnavailableException | CatedraResponseException | CatedraAuthenticationException
                        | CatedraRejectedException e) {
                    catedraFailed = true;
                    outcome = ExpirationOutcome.UNVERIFIED;
                }
            }
            results.add(new ExpirationResult(process.getId(), process.getStatus(), outcome));
        }
        return results;
    }
}
