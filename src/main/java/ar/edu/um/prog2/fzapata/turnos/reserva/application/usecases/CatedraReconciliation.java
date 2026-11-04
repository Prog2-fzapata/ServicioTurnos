package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

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
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import lombok.RequiredArgsConstructor;

// Lo que comparten el vencimiento y la recuperación al arrancar: preguntarle a la cátedra si ya tiene confirmado un
// proceso, y aplicar un cambio sin que un choque de versión o un proceso que otro adelantó frenen a quien llama. Sin
// transacción abierta: la consulta a la cátedra no participa de la de la base
@Component
@RequiredArgsConstructor
class CatedraReconciliation {

    // Un paciente tiene muy pocos turnos confirmados en un mismo día: una página alcanza. Si no alcanzara no se actúa
    private static final int CONFIRMED_PAGE_SIZE = 100;

    // CONFIRMED: la cátedra lo tiene confirmado. NOT_CONFIRMED: respondió completo y no figura. INCOMPLETE: hay más
    // resultados que los de la página y el proceso no estaba entre ellos, no se puede afirmar que no exista.
    // CATEDRA_FAILED: no se pudo preguntar
    enum Verdict {
        CONFIRMED, NOT_CONFIRMED, INCOMPLETE, CATEDRA_FAILED
    }

    record Answer(Verdict verdict, CatedraAppointment appointment) {

        static Answer of(Verdict verdict) {
            return new Answer(verdict, null);
        }
    }

    // APPLIED: el cambio se hizo. UNCHANGED: otro se adelantó y el paso ya no corresponde. CONFLICT: la versión chocó
    // más veces de las permitidas
    enum Applied {
        APPLIED, UNCHANGED, CONFLICT
    }

    private final ApplyReservationTransitionUseCase applyReservationTransition;
    private final CatedraApiClient catedraApiClient;

    // Ante la duda no se actúa: un EXPIRED es final y descartaría una confirmación que la cátedra sí tiene
    Answer askIfConfirmed(ReservationProcess process) {
        try {
            AppointmentPage page = catedraApiClient.findAppointments(new AppointmentQuery(process.getUserId(),
                    AppointmentStatus.CONFIRMED, process.getAppointmentDate(), process.getAppointmentDate()), 0,
                    CONFIRMED_PAGE_SIZE);
            Optional<CatedraAppointment> match = page.content().stream()
                    .filter(appointment -> process.getReservationProcessId() != null
                            && process.getReservationProcessId().equals(appointment.getReservationProcessId()))
                    .findFirst();
            if (match.isPresent()) {
                return new Answer(Verdict.CONFIRMED, match.get());
            }
            boolean complete = page.totalElements() <= page.content().size();
            return Answer.of(complete ? Verdict.NOT_CONFIRMED : Verdict.INCOMPLETE);
        } catch (CatedraUnavailableException | CatedraResponseException | CatedraAuthenticationException
                | CatedraRejectedException e) {
            return Answer.of(Verdict.CATEDRA_FAILED);
        }
    }

    ReservationTransition confirmedBy(CatedraAppointment appointment) {
        return ReservationTransition.builder().target(ReservationStatus.CONFIRMED)
                .reservationId(appointment.getReservationId()).build();
    }

    Applied apply(Long processId, ReservationTransition transition) {
        try {
            boolean applied = applyReservationTransition.applyTransition(processId, transition)
                    .filter(result -> result.outcome() == TransitionOutcome.APPLIED).isPresent();
            return applied ? Applied.APPLIED : Applied.UNCHANGED;
        } catch (ReservationConflictException e) {
            return Applied.CONFLICT;
        }
    }
}
