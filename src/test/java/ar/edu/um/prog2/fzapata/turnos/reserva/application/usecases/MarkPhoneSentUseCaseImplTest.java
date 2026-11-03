package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.OutboxRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class MarkPhoneSentUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-04T13:00:05Z");

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ReservationProcessRepository reservationProcessRepository;

    @Mock
    private ApplyReservationTransitionUseCase applyReservationTransition;

    private MarkPhoneSentUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new MarkPhoneSentUseCaseImpl(outboxRepository, reservationProcessRepository,
                applyReservationTransition, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private static OutboxEvent pending() {
        return OutboxEvent.builder().id(3L).eventId("phone-submitted-1").reservationProcessId("process-1")
                .status(OutboxStatus.PENDING).build();
    }

    private void processExists() {
        when(reservationProcessRepository.findByReservationProcessId("process-1")).thenReturn(Optional.of(
                ReservationProcess.builder().id(7L).reservationProcessId("process-1")
                        .status(ReservationStatus.PHONE_QUEUED).build()));
    }

    private TransitionResult result(TransitionOutcome outcome) {
        return new TransitionResult(outcome, ReservationStatus.PHONE_QUEUED, ReservationStatus.PHONE_SUBMITTED, null);
    }

    @Test
    void marksTheMessageAsSentAndMovesTheProcessToSubmitted() {
        when(outboxRepository.findById(3L)).thenReturn(Optional.of(pending()));
        processExists();
        TransitionResult applied = result(TransitionOutcome.APPLIED);
        when(applyReservationTransition.applyTransitionOnce(eq(7L), any())).thenReturn(Optional.of(applied));

        Optional<TransitionResult> outcome = useCase.markSent(pending());

        assertThat(outcome).containsSame(applied);
        ArgumentCaptor<ReservationTransition> transition = ArgumentCaptor.forClass(ReservationTransition.class);
        verify(applyReservationTransition).applyTransitionOnce(eq(7L), transition.capture());
        assertThat(transition.getValue().target()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(saved.getValue().getSentAt()).isEqualTo(NOW);
    }

    // La cátedra pudo contestar antes del ack: el diagrama rechaza el paso, el proceso no retrocede y el mensaje igual
    // queda enviado
    @Test
    void ifTheProcessAlreadyAdvancedTheStepIsIgnoredButTheMessageIsStillMarkedSent() {
        when(outboxRepository.findById(3L)).thenReturn(Optional.of(pending()));
        processExists();
        when(applyReservationTransition.applyTransitionOnce(eq(7L), any()))
                .thenReturn(Optional.of(result(TransitionOutcome.REJECTED)));

        Optional<TransitionResult> outcome = useCase.markSent(pending());

        assertThat(outcome).isPresent();
        assertThat(outcome.get().outcome()).isEqualTo(TransitionOutcome.REJECTED);
        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void aMessageAlreadySentIsEmptyAndNothingChanges() {
        OutboxEvent sent = pending();
        sent.setStatus(OutboxStatus.SENT);
        when(outboxRepository.findById(3L)).thenReturn(Optional.of(sent));

        assertThat(useCase.markSent(pending())).isEmpty();

        verifyNoInteractions(reservationProcessRepository, applyReservationTransition);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void usesTheSingleAttemptBecauseItRunsInsideATransaction() {
        when(outboxRepository.findById(3L)).thenReturn(Optional.of(pending()));
        processExists();
        when(applyReservationTransition.applyTransitionOnce(eq(7L), any()))
                .thenReturn(Optional.of(result(TransitionOutcome.APPLIED)));

        useCase.markSent(pending());

        verify(applyReservationTransition, never()).applyTransition(any(), any());
    }

    @Test
    void aVersionConflictPropagatesAndTheMessageIsNotMarked() {
        when(outboxRepository.findById(3L)).thenReturn(Optional.of(pending()));
        processExists();
        when(applyReservationTransition.applyTransitionOnce(eq(7L), any()))
                .thenThrow(new ReservationConflictException(7L, null));
        OutboxEvent event = pending();

        assertThatThrownBy(() -> useCase.markSent(event)).isInstanceOf(ReservationConflictException.class);

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void aMissingMessageIsAnError() {
        when(outboxRepository.findById(3L)).thenReturn(Optional.empty());
        OutboxEvent event = pending();

        assertThatThrownBy(() -> useCase.markSent(event)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMessageWithoutProcessIsAnError() {
        when(outboxRepository.findById(3L)).thenReturn(Optional.of(pending()));
        when(reservationProcessRepository.findByReservationProcessId("process-1")).thenReturn(Optional.empty());
        OutboxEvent event = pending();

        assertThatThrownBy(() -> useCase.markSent(event)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("phone-submitted-1");

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void aProcessThatDisappearedWhileApplyingIsNotFound() {
        when(outboxRepository.findById(3L)).thenReturn(Optional.of(pending()));
        processExists();
        when(applyReservationTransition.applyTransitionOnce(eq(7L), any())).thenReturn(Optional.empty());
        OutboxEvent event = pending();

        assertThatThrownBy(() -> useCase.markSent(event)).isInstanceOf(ReservationProcessNotFoundException.class);

        verify(outboxRepository, never()).save(any());
    }
}
