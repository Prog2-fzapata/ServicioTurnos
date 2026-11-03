package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class ApplyReservationTransitionUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-02T14:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-11-02T13:00:00Z");
    private static final Long ID = 7L;

    @Mock
    private ReservationProcessRepository repository;

    private static ReservationProcess process(ReservationStatus status) {
        return ReservationProcess.builder().id(ID).userId("42").professionalId(15L).status(status).createdAt(BEFORE)
                .updatedAt(BEFORE).version(3L).build();
    }

    private static ConflictRetryPolicy attempts(int maxAttempts) {
        return new ConflictRetryPolicy(maxAttempts);
    }

    private ApplyReservationTransitionUseCaseImpl useCase(int maxAttempts) {
        return new ApplyReservationTransitionUseCaseImpl(repository, attempts(maxAttempts),
                Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private ApplyReservationTransitionUseCaseImpl useCase() {
        return useCase(3);
    }

    private void saveReturnsWhatItReceives() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ReservationProcess savedProcess() {
        ArgumentCaptor<ReservationProcess> captor = ArgumentCaptor.forClass(ReservationProcess.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    static Stream<Arguments> everyStepOutOfAFinalStatus() {
        return Stream.of(ReservationStatus.EXPIRED, ReservationStatus.FAILED, ReservationStatus.CANCELLED)
                .flatMap(finalStatus -> Arrays.stream(ReservationStatus.values())
                        .map(target -> Arguments.of(finalStatus, target)));
    }

    @Test
    void anAllowedStepChangesTheStatusAndSavesIt() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)));
        saveReturnsWhatItReceives();

        TransitionResult result = useCase()
                .applyTransition(ID, ReservationTransition.to(ReservationStatus.WAITING_PHONE)).orElseThrow();

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.APPLIED);
        assertThat(result.from()).isEqualTo(ReservationStatus.HELD);
        assertThat(result.target()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(result.process().getStatus()).isEqualTo(ReservationStatus.WAITING_PHONE);
        ReservationProcess saved = savedProcess();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(saved.getCreatedAt()).isEqualTo(BEFORE);
        assertThat(saved.getVersion()).isEqualTo(3L);
    }

    @Test
    void theResultCarriesTheProcessAsSaved() {
        ReservationProcess storedAfterSaving = process(ReservationStatus.WAITING_PHONE);
        storedAfterSaving.setVersion(4L);
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)));
        when(repository.save(any())).thenReturn(storedAfterSaving);

        TransitionResult result = useCase()
                .applyTransition(ID, ReservationTransition.to(ReservationStatus.WAITING_PHONE)).orElseThrow();

        assertThat(result.process()).isSameAs(storedAfterSaving);
    }

    @Test
    void copiesEveryDataFieldOfTheTransition() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)));
        saveReturnsWhatItReceives();
        Instant expiresAt = Instant.parse("2026-11-02T14:10:00Z");

        useCase().applyTransition(ID, ReservationTransition.builder().target(ReservationStatus.WAITING_PHONE)
                .holdId("hold-1").reservationProcessId("process-1").expiresAt(expiresAt).patientPhone("+5492615551234")
                .requestEventId("event-1").reservationId(99L).failureReason("motivo").cancellationReason("razón")
                .build());

        ReservationProcess saved = savedProcess();
        assertThat(saved.getHoldId()).isEqualTo("hold-1");
        assertThat(saved.getReservationProcessId()).isEqualTo("process-1");
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(saved.getPatientPhone()).isEqualTo("+5492615551234");
        assertThat(saved.getRequestEventId()).isEqualTo("event-1");
        assertThat(saved.getReservationId()).isEqualTo(99L);
        assertThat(saved.getFailureReason()).isEqualTo("motivo");
        assertThat(saved.getCancellationReason()).isEqualTo("razón");
    }

    @Test
    void aMissingDataFieldNeverErasesWhatTheProcessAlreadyHad() {
        ReservationProcess existing = process(ReservationStatus.WAITING_PHONE);
        existing.setHoldId("hold-1");
        existing.setReservationProcessId("process-1");
        existing.setExpiresAt(Instant.parse("2026-11-02T14:10:00Z"));
        existing.setPatientPhone("+5492615551234");
        existing.setRequestEventId("event-1");
        existing.setReservationId(99L);
        existing.setFailureReason("motivo");
        existing.setCancellationReason("razón");
        when(repository.findById(ID)).thenReturn(Optional.of(existing));
        saveReturnsWhatItReceives();

        useCase().applyTransition(ID, ReservationTransition.to(ReservationStatus.PHONE_QUEUED));

        ReservationProcess saved = savedProcess();
        assertThat(saved.getHoldId()).isEqualTo("hold-1");
        assertThat(saved.getReservationProcessId()).isEqualTo("process-1");
        assertThat(saved.getExpiresAt()).isEqualTo(Instant.parse("2026-11-02T14:10:00Z"));
        assertThat(saved.getPatientPhone()).isEqualTo("+5492615551234");
        assertThat(saved.getRequestEventId()).isEqualTo("event-1");
        assertThat(saved.getReservationId()).isEqualTo(99L);
        assertThat(saved.getFailureReason()).isEqualTo("motivo");
        assertThat(saved.getCancellationReason()).isEqualTo("razón");
    }

    @Test
    void cancellingRecordsWhenItHappenedAndTheReason() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.CONFIRMED)));
        saveReturnsWhatItReceives();

        useCase().applyTransition(ID, ReservationTransition.builder().target(ReservationStatus.CANCELLED)
                .cancellationReason("Cambio solicitado").build());

        ReservationProcess saved = savedProcess();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(saved.getCancelledAt()).isEqualTo(NOW);
        assertThat(saved.getCancellationReason()).isEqualTo("Cambio solicitado");
    }

    @Test
    void onlyCancellingRecordsTheCancellationInstant() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)));
        saveReturnsWhatItReceives();

        useCase().applyTransition(ID, ReservationTransition.to(ReservationStatus.FAILED));

        assertThat(savedProcess().getCancelledAt()).isNull();
    }

    // Repetir un paso ya dado (un evento duplicado, una cancelación repetida) no cambia nada ni escribe en la base
    @Test
    void repeatingTheCurrentStatusChangesNothingAndDoesNotSave() {
        ReservationProcess existing = process(ReservationStatus.WAITING_PHONE);
        when(repository.findById(ID)).thenReturn(Optional.of(existing));

        TransitionResult result = useCase().applyTransition(ID, ReservationTransition.builder()
                .target(ReservationStatus.WAITING_PHONE).failureReason("no debería copiarse").build()).orElseThrow();

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.ALREADY_IN_STATE);
        assertThat(result.from()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(result.process().getFailureReason()).isNull();
        assertThat(result.process().getUpdatedAt()).isEqualTo(BEFORE);
        verify(repository, never()).save(any());
    }

    @Test
    void aStepTheDiagramDoesNotAllowIsRejectedAndNothingIsSaved() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)));

        TransitionResult result = useCase().applyTransition(ID, ReservationTransition.builder()
                .target(ReservationStatus.CONFIRMED).reservationId(5L).build()).orElseThrow();

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.REJECTED);
        assertThat(result.from()).isEqualTo(ReservationStatus.HELD);
        assertThat(result.target()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.process().getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(result.process().getReservationId()).isNull();
        verify(repository, never()).save(any());
    }

    // Un evento tardío o duplicado no reabre un proceso terminado, ni cambia sus datos
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyStepOutOfAFinalStatus")
    void aFinalProcessNeverChanges(ReservationStatus finalStatus, ReservationStatus target) {
        ReservationProcess existing = process(finalStatus);
        when(repository.findById(ID)).thenReturn(Optional.of(existing));

        TransitionResult result = useCase().applyTransition(ID, ReservationTransition.builder().target(target)
                .failureReason("tardío").reservationId(1L).build()).orElseThrow();

        assertThat(result.outcome())
                .isEqualTo(target == finalStatus ? TransitionOutcome.ALREADY_IN_STATE : TransitionOutcome.REJECTED);
        assertThat(result.process().getStatus()).isEqualTo(finalStatus);
        assertThat(result.process().getFailureReason()).isNull();
        assertThat(result.process().getReservationId()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void aMissingProcessIsEmptyAndNothingIsSaved() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThat(useCase().applyTransition(ID, ReservationTransition.to(ReservationStatus.FAILED))).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void aConflictRereadsTheProcessAndTriesAgain() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)),
                Optional.of(process(ReservationStatus.HELD)));
        when(repository.save(any())).thenThrow(new ReservationConflictException(ID, null))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransitionResult result = useCase()
                .applyTransition(ID, ReservationTransition.to(ReservationStatus.WAITING_PHONE)).orElseThrow();

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.APPLIED);
        verify(repository, times(2)).findById(ID);
        verify(repository, times(2)).save(any());
    }

    // Lo que decidió el primer intento puede ya no valer: se decide de nuevo con el estado vigente
    @Test
    void afterAConflictTheStepIsDecidedAgainOnTheCurrentStatus() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)),
                Optional.of(process(ReservationStatus.EXPIRED)));
        when(repository.save(any())).thenThrow(new ReservationConflictException(ID, null));

        TransitionResult result = useCase()
                .applyTransition(ID, ReservationTransition.to(ReservationStatus.WAITING_PHONE)).orElseThrow();

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.REJECTED);
        assertThat(result.from()).isEqualTo(ReservationStatus.EXPIRED);
        verify(repository, times(1)).save(any());
    }

    @Test
    void afterAConflictAStepAlreadyDoneByTheOtherOperationIsNotRepeated() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)),
                Optional.of(process(ReservationStatus.WAITING_PHONE)));
        when(repository.save(any())).thenThrow(new ReservationConflictException(ID, null));

        TransitionResult result = useCase()
                .applyTransition(ID, ReservationTransition.to(ReservationStatus.WAITING_PHONE)).orElseThrow();

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.ALREADY_IN_STATE);
        verify(repository, times(1)).save(any());
    }

    @Test
    void afterAConflictTheProcessMayHaveDisappeared() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)), Optional.empty());
        when(repository.save(any())).thenThrow(new ReservationConflictException(ID, null));

        assertThat(useCase().applyTransition(ID, ReservationTransition.to(ReservationStatus.WAITING_PHONE)))
                .isEmpty();
    }

    @Test
    void givesUpAfterExactlyTheConfiguredAttempts() {
        when(repository.findById(ID)).thenAnswer(invocation -> Optional.of(process(ReservationStatus.HELD)));
        when(repository.save(any())).thenThrow(new ReservationConflictException(ID, null));

        ApplyReservationTransitionUseCaseImpl useCase = useCase(3);
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.WAITING_PHONE);

        assertThatThrownBy(() -> useCase.applyTransition(ID, transition))
                .isInstanceOf(ReservationConflictException.class);
        verify(repository, times(3)).findById(ID);
        verify(repository, times(3)).save(any());
    }

    @Test
    void oneAttemptDisablesTheRetry() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)));
        when(repository.save(any())).thenThrow(new ReservationConflictException(ID, null));

        ApplyReservationTransitionUseCaseImpl useCase = useCase(1);
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.WAITING_PHONE);

        assertThatThrownBy(() -> useCase.applyTransition(ID, transition))
                .isInstanceOf(ReservationConflictException.class);
        verify(repository, times(1)).save(any());
    }

    @Test
    void anyOtherFailureIsNotRetried() {
        when(repository.findById(ID)).thenReturn(Optional.of(process(ReservationStatus.HELD)));
        when(repository.save(any())).thenThrow(new IllegalStateException("la base se cayó"));

        ApplyReservationTransitionUseCaseImpl useCase = useCase();
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.WAITING_PHONE);

        assertThatThrownBy(() -> useCase.applyTransition(ID, transition)).isInstanceOf(IllegalStateException.class);
        verify(repository, times(1)).save(any());
    }
}
