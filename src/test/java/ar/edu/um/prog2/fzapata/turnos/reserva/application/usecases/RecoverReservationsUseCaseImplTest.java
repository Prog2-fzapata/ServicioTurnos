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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentPage;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraAppointment;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class RecoverReservationsUseCaseImplTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-04T14:00:00Z");
    private static final int BATCH = 100;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);

    @Mock
    private ReservationProcessRepository repository;

    @Mock
    private ApplyReservationTransitionUseCase transitions;

    @Mock
    private CatedraApiClient catedra;

    private RecoverReservationsUseCaseImpl useCase() {
        return new RecoverReservationsUseCaseImpl(repository, new CatedraReconciliation(transitions, catedra),
                new RecoveryPolicy(BATCH));
    }

    private static ReservationProcess process(long id, ReservationStatus status) {
        return ReservationProcess.builder().id(id).userId("42").reservationProcessId("process-" + id)
                .professionalId(15L).appointmentDate(DATE).status(status).build();
    }

    private static ReservationProcess orphan(long id) {
        return ReservationProcess.builder().id(id).userId("42").professionalId(15L).appointmentDate(DATE)
                .status(ReservationStatus.HOLD_REQUESTED).build();
    }

    private static CatedraAppointment confirmed(String processId, long reservationId) {
        return CatedraAppointment.builder().reservationId(reservationId).reservationProcessId(processId)
                .status(AppointmentStatus.CONFIRMED).build();
    }

    private void doubtful(List<ReservationProcess> orphans, List<ReservationProcess> withPhone) {
        when(repository.findStaleHoldRequests(any(), anyInt())).thenReturn(orphans);
        when(repository.findWithPhoneSent(anyInt())).thenReturn(withPhone);
    }

    private void transitionIs(TransitionOutcome outcome) {
        when(transitions.applyTransition(any(), any())).thenReturn(Optional.of(
                new TransitionResult(outcome, ReservationStatus.HOLD_REQUESTED, ReservationStatus.FAILED, null)));
    }

    private void catedraAnswers(List<CatedraAppointment> content, long total) {
        when(catedra.findAppointments(any(), eq(0), anyInt())).thenReturn(new AppointmentPage(content, total));
    }

    private ReservationTransition appliedTo(long processId) {
        ArgumentCaptor<ReservationTransition> captor = ArgumentCaptor.forClass(ReservationTransition.class);
        verify(transitions).applyTransition(eq(processId), captor.capture());
        return captor.getValue();
    }

    @Test
    void asksForTheOrphansCreatedBeforeStartingAndForTheProcessesWithThePhoneSent() {
        doubtful(List.of(), List.of());

        List<RecoveryResult> results = useCase().recover(STARTED_AT);

        verify(repository).findStaleHoldRequests(STARTED_AT, BATCH);
        verify(repository).findWithPhoneSent(BATCH);
        assertThat(results).isEmpty();
        verifyNoInteractions(transitions, catedra);
    }

    // Nunca llegaron los ids del hold: no hay con qué confirmar ni reconciliar, y el hold huérfano vence solo
    @Test
    void anOrphanHoldRequestFailsWithTheSameReasonAsALostHoldResponse() {
        doubtful(List.of(orphan(1)), List.of());
        transitionIs(TransitionOutcome.APPLIED);

        List<RecoveryResult> results = useCase().recover(STARTED_AT);

        assertThat(results).containsExactly(new RecoveryResult(1L, ReservationStatus.HOLD_REQUESTED,
                RecoveryOutcome.ORPHAN_FAILED));
        ReservationTransition transition = appliedTo(1);
        assertThat(transition.target()).isEqualTo(ReservationStatus.FAILED);
        assertThat(transition.failureReason()).isEqualTo("HOLD_RESULT_UNKNOWN");
        verifyNoInteractions(catedra);
    }

    @ParameterizedTest
    @EnumSource(value = TransitionOutcome.class, names = {"REJECTED", "ALREADY_IN_STATE"})
    void anOrphanThatSomeoneElseClosedIsLeftAlone(TransitionOutcome outcome) {
        doubtful(List.of(orphan(1)), List.of());
        transitionIs(outcome);

        assertThat(useCase().recover(STARTED_AT).getFirst().outcome()).isEqualTo(RecoveryOutcome.UNCHANGED);
    }

    @Test
    void aVersionConflictOnOneOrphanDoesNotStopTheOthers() {
        doubtful(List.of(orphan(1), orphan(2)), List.of());
        when(transitions.applyTransition(eq(1L), any())).thenThrow(new ReservationConflictException(1L, null));
        when(transitions.applyTransition(eq(2L), any())).thenReturn(Optional.of(new TransitionResult(
                TransitionOutcome.APPLIED, ReservationStatus.HOLD_REQUESTED, ReservationStatus.FAILED, null)));

        List<RecoveryResult> results = useCase().recover(STARTED_AT);

        assertThat(results).extracting(RecoveryResult::outcome).containsExactly(RecoveryOutcome.CONFLICT,
                RecoveryOutcome.ORPHAN_FAILED);
    }

    // El caso que justifica la reconciliación: la confirmación llegó a la cátedra y el evento nunca llegó a turnos
    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"PHONE_QUEUED", "PHONE_SUBMITTED"})
    void aProcessTheCatedraHasConfirmedIsRepaired(ReservationStatus status) {
        doubtful(List.of(), List.of(process(5, status)));
        catedraAnswers(List.of(confirmed("process-5", 8251)), 1);
        transitionIs(TransitionOutcome.APPLIED);

        List<RecoveryResult> results = useCase().recover(STARTED_AT);

        assertThat(results).containsExactly(new RecoveryResult(5L, status, RecoveryOutcome.CONFIRMED_BY_CATEDRA));
        ReservationTransition transition = appliedTo(5);
        assertThat(transition.target()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(transition.reservationId()).isEqualTo(8251L);
    }

    // Solo se actúa con evidencia: que la cátedra no lo tenga confirmado no prueba que haya que cerrarlo
    @Test
    void aProcessTheCatedraDoesNotHaveConfirmedIsLeftAsItIs() {
        doubtful(List.of(), List.of(process(5, ReservationStatus.PHONE_SUBMITTED)));
        catedraAnswers(List.of(), 0);

        List<RecoveryResult> results = useCase().recover(STARTED_AT);

        assertThat(results).containsExactly(new RecoveryResult(5L, ReservationStatus.PHONE_SUBMITTED,
                RecoveryOutcome.STILL_OPEN));
        verifyNoInteractions(transitions);
    }

    @Test
    void anIncompleteAnswerWithoutTheProcessTouchesNothing() {
        doubtful(List.of(), List.of(process(5, ReservationStatus.PHONE_SUBMITTED)));
        catedraAnswers(List.of(confirmed("process-99", 700)), 150);

        assertThat(useCase().recover(STARTED_AT).getFirst().outcome()).isEqualTo(RecoveryOutcome.UNVERIFIED);
        verifyNoInteractions(transitions);
    }

    @ParameterizedTest
    @EnumSource(value = TransitionOutcome.class, names = {"REJECTED", "ALREADY_IN_STATE"})
    void aRepairThatSomeoneElseDidFirstIsUnchanged(TransitionOutcome outcome) {
        doubtful(List.of(), List.of(process(5, ReservationStatus.PHONE_SUBMITTED)));
        catedraAnswers(List.of(confirmed("process-5", 8251)), 1);
        transitionIs(outcome);

        assertThat(useCase().recover(STARTED_AT).getFirst().outcome()).isEqualTo(RecoveryOutcome.UNCHANGED);
    }

    @Test
    void aVersionConflictOnTheRepairIsReportedAndTheNextProcessIsStillChecked() {
        doubtful(List.of(), List.of(process(5, ReservationStatus.PHONE_SUBMITTED),
                process(6, ReservationStatus.PHONE_QUEUED)));
        catedraAnswers(List.of(confirmed("process-5", 8251), confirmed("process-6", 8252)), 2);
        when(transitions.applyTransition(eq(5L), any())).thenThrow(new ReservationConflictException(5L, null));
        when(transitions.applyTransition(eq(6L), any())).thenReturn(Optional.of(new TransitionResult(
                TransitionOutcome.APPLIED, ReservationStatus.PHONE_QUEUED, ReservationStatus.CONFIRMED, null)));

        List<RecoveryResult> results = useCase().recover(STARTED_AT);

        assertThat(results).extracting(RecoveryResult::outcome).containsExactly(RecoveryOutcome.CONFLICT,
                RecoveryOutcome.CONFIRMED_BY_CATEDRA);
    }

    // Después del primer fallo no se insiste; los huérfanos, que no necesitan a la cátedra, ya se cerraron antes
    @Test
    void afterTheFirstCatedraFailureItIsNotAskedAgainButTheOrphansAreStillFailed() {
        doubtful(List.of(orphan(1)), List.of(process(5, ReservationStatus.PHONE_SUBMITTED),
                process(6, ReservationStatus.PHONE_QUEUED)));
        when(catedra.findAppointments(any(), anyInt(), anyInt()))
                .thenThrow(new CatedraNotReachedException("sin conexión", null));
        transitionIs(TransitionOutcome.APPLIED);

        List<RecoveryResult> results = useCase().recover(STARTED_AT);

        assertThat(results).extracting(RecoveryResult::outcome).containsExactly(RecoveryOutcome.ORPHAN_FAILED,
                RecoveryOutcome.UNVERIFIED, RecoveryOutcome.UNVERIFIED);
        verify(catedra, times(1)).findAppointments(any(), anyInt(), anyInt());
        verify(transitions, times(1)).applyTransition(any(), any());
        verify(transitions, never()).applyTransition(eq(5L), any());
    }

    @Test
    void theOrphansComeFirstAndEachGroupKeepsItsOrder() {
        doubtful(List.of(orphan(9), orphan(3)), List.of(process(5, ReservationStatus.PHONE_SUBMITTED),
                process(2, ReservationStatus.PHONE_QUEUED)));
        catedraAnswers(List.of(), 0);
        transitionIs(TransitionOutcome.APPLIED);

        assertThat(useCase().recover(STARTED_AT)).extracting(RecoveryResult::processId).containsExactly(9L, 3L, 5L,
                2L);
    }
}
