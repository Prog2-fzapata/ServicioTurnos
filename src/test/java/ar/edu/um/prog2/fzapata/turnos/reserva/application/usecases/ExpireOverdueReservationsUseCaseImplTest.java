package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraAuthenticationException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentPage;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraAppointment;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class ExpireOverdueReservationsUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-04T14:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(60);
    private static final int BATCH = 100;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);

    @Mock
    private ReservationProcessRepository repository;

    @Mock
    private ApplyReservationTransitionUseCase transitions;

    @Mock
    private CatedraApiClient catedra;

    private ExpireOverdueReservationsUseCaseImpl useCase() {
        return new ExpireOverdueReservationsUseCaseImpl(repository, new CatedraReconciliation(transitions, catedra),
                new ExpirationPolicy(GRACE, BATCH), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ReservationProcess process(long id, ReservationStatus status) {
        return ReservationProcess.builder().id(id).userId("42").reservationProcessId("process-" + id)
                .professionalId(15L).appointmentDate(DATE).status(status).expiresAt(NOW.minusSeconds(300)).build();
    }

    private static CatedraAppointment confirmed(String processId, long reservationId) {
        return CatedraAppointment.builder().reservationId(reservationId).reservationProcessId(processId)
                .status(AppointmentStatus.CONFIRMED).build();
    }

    private void overdue(ReservationProcess... processes) {
        when(repository.findExpirable(any(), anyInt())).thenReturn(List.of(processes));
    }

    private void transitionIs(TransitionOutcome outcome) {
        when(transitions.applyTransition(any(), any())).thenReturn(Optional.of(
                new TransitionResult(outcome, ReservationStatus.HELD, ReservationStatus.EXPIRED, null)));
    }

    private void catedraAnswers(List<CatedraAppointment> content, long total) {
        when(catedra.findAppointments(any(), eq(0), anyInt())).thenReturn(new AppointmentPage(content, total));
    }

    private ReservationTransition appliedTo(long processId) {
        ArgumentCaptor<ReservationTransition> captor = ArgumentCaptor.forClass(ReservationTransition.class);
        verify(transitions).applyTransition(eq(processId), captor.capture());
        return captor.getValue();
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

    // Los candidatos son los vencidos hace más que el margen, y no más de una tanda
    @Test
    void asksForTheProcessesExpiredBeyondTheGraceAndNoMoreThanOneBatch() {
        overdue();

        List<ExpirationResult> results = useCase().expireOverdue();

        verify(repository).findExpirable(NOW.minus(GRACE), BATCH);
        assertThat(results).isEmpty();
        verifyNoInteractions(transitions, catedra);
    }

    // Sin teléfono enviado la cátedra no tiene con qué haber confirmado: no hace falta preguntarle
    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"HELD", "WAITING_PHONE"})
    void aProcessWithoutAPhoneSentIsExpiredWithoutAskingTheCatedra(ReservationStatus status) {
        overdue(process(1, status));
        transitionIs(TransitionOutcome.APPLIED);

        List<ExpirationResult> results = useCase().expireOverdue();

        assertThat(results).containsExactly(new ExpirationResult(1L, status, ExpirationOutcome.EXPIRED));
        ReservationTransition transition = appliedTo(1);
        assertThat(transition.target()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(transition.failureReason()).isEqualTo("HOLD_EXPIRED");
        verifyNoInteractions(catedra);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"PHONE_QUEUED", "PHONE_SUBMITTED"})
    void aProcessWithAPhoneIsExpiredOnlyIfTheCatedraDoesNotHaveItConfirmed(ReservationStatus status) {
        overdue(process(1, status));
        catedraAnswers(List.of(), 0);
        transitionIs(TransitionOutcome.APPLIED);

        List<ExpirationResult> results = useCase().expireOverdue();

        assertThat(results).containsExactly(new ExpirationResult(1L, status, ExpirationOutcome.EXPIRED));
        assertThat(appliedTo(1).target()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void theCatedraIsAskedForTheConfirmedAppointmentsOfThatPatientOnThatDay() {
        overdue(process(1, ReservationStatus.PHONE_SUBMITTED));
        catedraAnswers(List.of(), 0);
        transitionIs(TransitionOutcome.APPLIED);

        useCase().expireOverdue();

        verify(catedra).findAppointments(new AppointmentQuery("42", AppointmentStatus.CONFIRMED, DATE, DATE), 0, 100);
    }

    // El caso que justifica la consulta: la confirmación llegó a la cátedra y el evento todavía no a turnos
    @Test
    void aProcessTheCatedraHasConfirmedIsRepairedInsteadOfExpired() {
        overdue(process(1, ReservationStatus.PHONE_SUBMITTED));
        catedraAnswers(List.of(confirmed("process-1", 8251)), 1);
        transitionIs(TransitionOutcome.APPLIED);

        List<ExpirationResult> results = useCase().expireOverdue();

        assertThat(results).containsExactly(new ExpirationResult(1L, ReservationStatus.PHONE_SUBMITTED,
                ExpirationOutcome.CONFIRMED_BY_CATEDRA));
        ReservationTransition transition = appliedTo(1);
        assertThat(transition.target()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(transition.reservationId()).isEqualTo(8251L);
        assertThat(transition.failureReason()).isNull();
    }

    // La cátedra devuelve las reservas de toda la cuenta: solo cuenta la de este proceso
    @Test
    void anAppointmentOfAnotherProcessDoesNotCount() {
        overdue(process(1, ReservationStatus.PHONE_SUBMITTED));
        catedraAnswers(List.of(confirmed("process-99", 700)), 1);
        transitionIs(TransitionOutcome.APPLIED);

        useCase().expireOverdue();

        assertThat(appliedTo(1).target()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void aProcessWithoutAnIdNeverMatchesAnAppointmentWithoutOne() {
        ReservationProcess withoutId = process(1, ReservationStatus.PHONE_QUEUED);
        withoutId.setReservationProcessId(null);
        overdue(withoutId);
        catedraAnswers(List.of(confirmed(null, 700)), 1);
        transitionIs(TransitionOutcome.APPLIED);

        useCase().expireOverdue();

        assertThat(appliedTo(1).target()).isEqualTo(ReservationStatus.EXPIRED);
    }

    // Hay más resultados que los de la página y el proceso no está entre ellos: no se puede afirmar que no exista
    @Test
    void ifTheAnswerIsIncompleteAndTheProcessIsNotInItNothingIsExpired() {
        overdue(process(1, ReservationStatus.PHONE_SUBMITTED));
        catedraAnswers(List.of(confirmed("process-99", 700)), 150);

        List<ExpirationResult> results = useCase().expireOverdue();

        assertThat(results).containsExactly(new ExpirationResult(1L, ReservationStatus.PHONE_SUBMITTED,
                ExpirationOutcome.UNVERIFIED));
        verifyNoInteractions(transitions);
    }

    @Test
    void ifTheAnswerIsIncompleteButTheProcessIsInItItIsRepaired() {
        overdue(process(1, ReservationStatus.PHONE_SUBMITTED));
        catedraAnswers(List.of(confirmed("process-1", 8251)), 150);
        transitionIs(TransitionOutcome.APPLIED);

        List<ExpirationResult> results = useCase().expireOverdue();

        assertThat(results.getFirst().outcome()).isEqualTo(ExpirationOutcome.CONFIRMED_BY_CATEDRA);
    }

    // Ante la duda no se expira: un EXPIRED es final y descartaría una confirmación que la cátedra sí tiene
    @ParameterizedTest(name = "{0}")
    @MethodSource("catedraFailures")
    void ifTheCatedraCannotBeAskedNothingIsExpired(String name, Supplier<RuntimeException> failure) {
        overdue(process(1, ReservationStatus.PHONE_SUBMITTED));
        when(catedra.findAppointments(any(), anyInt(), anyInt())).thenThrow(failure.get());

        List<ExpirationResult> results = useCase().expireOverdue();

        assertThat(results).containsExactly(new ExpirationResult(1L, ReservationStatus.PHONE_SUBMITTED,
                ExpirationOutcome.UNVERIFIED));
        verifyNoInteractions(transitions);
    }

    // Después del primer fallo no se insiste en esta vuelta; los que no necesitan consulta se expiran igual
    @Test
    void afterTheFirstCatedraFailureItIsNotAskedAgainButTheRestIsStillExpired() {
        overdue(process(1, ReservationStatus.PHONE_SUBMITTED), process(2, ReservationStatus.PHONE_QUEUED),
                process(3, ReservationStatus.HELD));
        when(catedra.findAppointments(any(), anyInt(), anyInt()))
                .thenThrow(new CatedraNotReachedException("sin conexión", null));
        transitionIs(TransitionOutcome.APPLIED);

        List<ExpirationResult> results = useCase().expireOverdue();

        assertThat(results).extracting(ExpirationResult::outcome).containsExactly(ExpirationOutcome.UNVERIFIED,
                ExpirationOutcome.UNVERIFIED, ExpirationOutcome.EXPIRED);
        verify(catedra, times(1)).findAppointments(any(), anyInt(), anyInt());
        verify(transitions, times(1)).applyTransition(any(), any());
        assertThat(appliedTo(3).target()).isEqualTo(ReservationStatus.EXPIRED);
    }

    // Otro (el evento de la cátedra, el usuario) se adelantó: el paso ya no corresponde y no es un error
    @ParameterizedTest
    @EnumSource(value = TransitionOutcome.class, names = {"REJECTED", "ALREADY_IN_STATE"})
    void ifSomeoneElseGotThereFirstTheProcessIsLeftAlone(TransitionOutcome outcome) {
        overdue(process(1, ReservationStatus.HELD));
        transitionIs(outcome);

        List<ExpirationResult> results = useCase().expireOverdue();

        assertThat(results.getFirst().outcome()).isEqualTo(ExpirationOutcome.UNCHANGED);
    }

    @Test
    void aProcessThatDisappearedIsLeftAlone() {
        overdue(process(1, ReservationStatus.HELD));
        when(transitions.applyTransition(any(), any())).thenReturn(Optional.empty());

        assertThat(useCase().expireOverdue().getFirst().outcome()).isEqualTo(ExpirationOutcome.UNCHANGED);
    }

    // Un choque de versión agotado en un proceso no frena a los demás
    @Test
    void aVersionConflictOnOneProcessDoesNotStopTheOthers() {
        overdue(process(1, ReservationStatus.HELD), process(2, ReservationStatus.WAITING_PHONE));
        when(transitions.applyTransition(eq(1L), any())).thenThrow(new ReservationConflictException(1L, null));
        when(transitions.applyTransition(eq(2L), any())).thenReturn(Optional.of(new TransitionResult(
                TransitionOutcome.APPLIED, ReservationStatus.WAITING_PHONE, ReservationStatus.EXPIRED, null)));

        List<ExpirationResult> results = useCase().expireOverdue();

        assertThat(results).extracting(ExpirationResult::outcome).containsExactly(ExpirationOutcome.CONFLICT,
                ExpirationOutcome.EXPIRED);
    }

    @Test
    void theResultsKeepTheOrderOfTheProcesses() {
        overdue(process(5, ReservationStatus.HELD), process(3, ReservationStatus.WAITING_PHONE),
                process(9, ReservationStatus.HELD));
        transitionIs(TransitionOutcome.APPLIED);

        assertThat(useCase().expireOverdue()).extracting(ExpirationResult::processId).containsExactly(5L, 3L, 9L);
        verify(transitions, never()).applyTransitionOnce(any(), any());
    }
}
