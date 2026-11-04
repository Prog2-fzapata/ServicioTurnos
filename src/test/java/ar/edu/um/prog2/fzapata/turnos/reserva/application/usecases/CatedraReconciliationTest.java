package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraAuthenticationException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases.CatedraReconciliation.Answer;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases.CatedraReconciliation.Applied;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases.CatedraReconciliation.Verdict;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentPage;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraAppointment;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;

@ExtendWith(MockitoExtension.class)
class CatedraReconciliationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);

    @Mock
    private ApplyReservationTransitionUseCase transitions;

    @Mock
    private CatedraApiClient catedra;

    private CatedraReconciliation reconciliation() {
        return new CatedraReconciliation(transitions, catedra);
    }

    private static ReservationProcess process() {
        return ReservationProcess.builder().id(7L).userId("42").reservationProcessId("process-7")
                .appointmentDate(DATE).status(ReservationStatus.PHONE_SUBMITTED).build();
    }

    private static CatedraAppointment confirmed(String processId, long reservationId) {
        return CatedraAppointment.builder().reservationId(reservationId).reservationProcessId(processId)
                .status(AppointmentStatus.CONFIRMED).build();
    }

    private void catedraAnswers(List<CatedraAppointment> content, long total) {
        when(catedra.findAppointments(any(), anyInt(), anyInt())).thenReturn(new AppointmentPage(content, total));
    }

    static Stream<Arguments> catedraFailures() {
        Supplier<RuntimeException> unavailable = () -> new CatedraUnavailableException("sin respuesta", null);
        Supplier<RuntimeException> notReached = () -> new CatedraNotReachedException("sin conexión", null);
        Supplier<RuntimeException> response = () -> new CatedraResponseException("respuesta ilegible", null);
        Supplier<RuntimeException> authentication = () -> new CatedraAuthenticationException("token rechazado", null);
        Supplier<RuntimeException> rejected = () -> new CatedraRejectedException(CatedraRejection.RESERVATION_NOT_FOUND);
        return Stream.of(Arguments.of("unavailable", unavailable), Arguments.of("not reached", notReached),
                Arguments.of("response", response), Arguments.of("authentication", authentication),
                Arguments.of("rejected", rejected));
    }

    @Test
    void asksForTheConfirmedAppointmentsOfThatPatientOnThatDay() {
        catedraAnswers(List.of(), 0);

        reconciliation().askIfConfirmed(process());

        verify(catedra).findAppointments(new AppointmentQuery("42", AppointmentStatus.CONFIRMED, DATE, DATE), 0, 100);
    }

    @Test
    void ifTheProcessIsAmongTheConfirmedOnesTheAnswerCarriesItsAppointment() {
        catedraAnswers(List.of(confirmed("process-99", 700), confirmed("process-7", 8251)), 2);

        Answer answer = reconciliation().askIfConfirmed(process());

        assertThat(answer.verdict()).isEqualTo(Verdict.CONFIRMED);
        assertThat(answer.appointment().getReservationId()).isEqualTo(8251L);
    }

    @Test
    void aCompleteAnswerWithoutTheProcessMeansNotConfirmed() {
        catedraAnswers(List.of(confirmed("process-99", 700)), 1);

        assertThat(reconciliation().askIfConfirmed(process()).verdict()).isEqualTo(Verdict.NOT_CONFIRMED);
    }

    // Hay más resultados que los de la página y el proceso no está entre ellos: no se puede afirmar que no exista
    @Test
    void anIncompleteAnswerWithoutTheProcessProvesNothing() {
        catedraAnswers(List.of(confirmed("process-99", 700)), 150);

        assertThat(reconciliation().askIfConfirmed(process()).verdict()).isEqualTo(Verdict.INCOMPLETE);
    }

    @Test
    void anIncompleteAnswerThatContainsTheProcessStillConfirmsIt() {
        catedraAnswers(List.of(confirmed("process-7", 8251)), 150);

        assertThat(reconciliation().askIfConfirmed(process()).verdict()).isEqualTo(Verdict.CONFIRMED);
    }

    @Test
    void aProcessWithoutAnIdNeverMatchesAnAppointmentWithoutOne() {
        ReservationProcess withoutId = process();
        withoutId.setReservationProcessId(null);
        catedraAnswers(List.of(confirmed(null, 700)), 1);

        assertThat(reconciliation().askIfConfirmed(withoutId).verdict()).isEqualTo(Verdict.NOT_CONFIRMED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catedraFailures")
    void whateverGoesWrongWithTheCatedraIsReportedNotThrown(String name, Supplier<RuntimeException> failure) {
        when(catedra.findAppointments(any(), anyInt(), anyInt())).thenThrow(failure.get());

        assertThat(reconciliation().askIfConfirmed(process()).verdict()).isEqualTo(Verdict.CATEDRA_FAILED);
    }

    @Test
    void theRepairConfirmsWithTheReservationIdOfTheCatedra() {
        ReservationTransition transition = reconciliation().confirmedBy(confirmed("process-7", 8251));

        assertThat(transition.target()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(transition.reservationId()).isEqualTo(8251L);
        assertThat(transition.failureReason()).isNull();
    }

    @Test
    void anAppliedTransitionIsReportedAsApplied() {
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.EXPIRED);
        when(transitions.applyTransition(7L, transition)).thenReturn(Optional.of(
                new TransitionResult(TransitionOutcome.APPLIED, ReservationStatus.HELD, ReservationStatus.EXPIRED, null)));

        assertThat(reconciliation().apply(7L, transition)).isEqualTo(Applied.APPLIED);
    }

    // Otro (el evento de la cátedra, el usuario) se adelantó: el paso ya no corresponde y no es un error
    @ParameterizedTest
    @EnumSource(value = TransitionOutcome.class, names = {"REJECTED", "ALREADY_IN_STATE", "DATA_COMPLETED"})
    void aTransitionThatDidNotChangeTheStatusIsUnchanged(TransitionOutcome outcome) {
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.EXPIRED);
        when(transitions.applyTransition(7L, transition)).thenReturn(Optional.of(
                new TransitionResult(outcome, ReservationStatus.HELD, ReservationStatus.EXPIRED, null)));

        assertThat(reconciliation().apply(7L, transition)).isEqualTo(Applied.UNCHANGED);
    }

    @Test
    void aProcessThatDisappearedIsUnchanged() {
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.EXPIRED);
        when(transitions.applyTransition(7L, transition)).thenReturn(Optional.empty());

        assertThat(reconciliation().apply(7L, transition)).isEqualTo(Applied.UNCHANGED);
    }

    @Test
    void aVersionConflictIsReportedNotThrown() {
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.EXPIRED);
        when(transitions.applyTransition(7L, transition)).thenThrow(new ReservationConflictException(7L, null));

        assertThat(reconciliation().apply(7L, transition)).isEqualTo(Applied.CONFLICT);
    }
}
