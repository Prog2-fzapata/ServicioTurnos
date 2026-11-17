package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ProcessedEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ProcessedEventRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class ProcessCatedraEventUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");
    private static final Long ID = 7L;
    private static final String PROCESS = "process-a92b47d0";

    @Mock
    private ReservationProcessRepository reservationProcessRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ApplyReservationTransitionUseCase applyReservationTransition;

    private ProcessCatedraEventUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessCatedraEventUseCaseImpl(reservationProcessRepository, processedEventRepository,
                applyReservationTransition, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private static CatedraEvent event(String eventId, String eventType) {
        return new CatedraEvent(eventId, eventType, PROCESS, 8251L, "motivo", null);
    }

    private void processExists() {
        when(reservationProcessRepository.findByReservationProcessId(PROCESS))
                .thenReturn(Optional.of(ReservationProcess.builder().id(ID).status(ReservationStatus.PHONE_SUBMITTED)
                        .reservationProcessId(PROCESS).build()));
    }

    private TransitionResult transitionResult(TransitionOutcome outcome) {
        return new TransitionResult(outcome, ReservationStatus.PHONE_SUBMITTED, ReservationStatus.CONFIRMED, null);
    }

    private ProcessedEvent recorded() {
        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void appliesTheTransitionOfTheEventAndThenRecordsTheEventId() {
        processExists();
        TransitionResult applied = transitionResult(TransitionOutcome.APPLIED);
        when(applyReservationTransition.applyTransitionOnce(eq(ID), any())).thenReturn(Optional.of(applied));

        EventResult result = useCase.processEvent(event("evento-1", "AppointmentConfirmed"));

        assertThat(result.outcome()).isEqualTo(EventOutcome.PROCESSED);
        assertThat(result.transition()).isSameAs(applied);
        ArgumentCaptor<ReservationTransition> transition = ArgumentCaptor.forClass(ReservationTransition.class);
        InOrder order = inOrder(applyReservationTransition, processedEventRepository);
        order.verify(applyReservationTransition).applyTransitionOnce(eq(ID), transition.capture());
        order.verify(processedEventRepository).save(any());
        assertThat(transition.getValue().target()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(transition.getValue().reservationId()).isEqualTo(8251L);
        ProcessedEvent saved = recorded();
        assertThat(saved.getEventId()).isEqualTo("evento-1");
        assertThat(saved.getEventType()).isEqualTo("AppointmentConfirmed");
        assertThat(saved.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void usesTheSingleAttemptOfTheTransitionBecauseItRunsInsideATransaction() {
        processExists();
        when(applyReservationTransition.applyTransitionOnce(eq(ID), any()))
                .thenReturn(Optional.of(transitionResult(TransitionOutcome.APPLIED)));

        useCase.processEvent(event("evento-1", "AppointmentConfirmed"));

        verify(applyReservationTransition, never()).applyTransition(any(), any());
    }

    @Test
    void aRepeatedEventIsDuplicatedAndNothingElseHappens() {
        when(processedEventRepository.existsById("evento-1")).thenReturn(true);

        EventResult result = useCase.processEvent(event("evento-1", "AppointmentConfirmed"));

        assertThat(result.outcome()).isEqualTo(EventOutcome.DUPLICATE);
        assertThat(result.transition()).isNull();
        verifyNoInteractions(reservationProcessRepository, applyReservationTransition);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void anUnknownProcessIsIgnoredButTheEventIdIsRecorded() {
        when(reservationProcessRepository.findByReservationProcessId(PROCESS)).thenReturn(Optional.empty());

        EventResult result = useCase.processEvent(event("evento-1", "AppointmentConfirmed"));

        assertThat(result.outcome()).isEqualTo(EventOutcome.UNKNOWN_PROCESS);
        verifyNoInteractions(applyReservationTransition);
        assertThat(recorded().getEventId()).isEqualTo("evento-1");
    }

    @Test
    void anUnknownTypeIsIgnoredWithoutLookingForTheProcessButTheEventIdIsRecordedWithItsType() {
        EventResult result = useCase.processEvent(event("evento-1", "AlgoNuevoDeLaCatedra"));

        assertThat(result.outcome()).isEqualTo(EventOutcome.UNKNOWN_TYPE);
        verifyNoInteractions(reservationProcessRepository, applyReservationTransition);
        assertThat(recorded().getEventType()).isEqualTo("AlgoNuevoDeLaCatedra");
    }

    // Un paso que el diagrama no permite (un evento tardío) se registra: reentregarlo no lo va a arreglar
    @Test
    void aRejectedStepIsProcessedAndRecorded() {
        processExists();
        TransitionResult rejected = transitionResult(TransitionOutcome.REJECTED);
        when(applyReservationTransition.applyTransitionOnce(eq(ID), any())).thenReturn(Optional.of(rejected));

        EventResult result = useCase.processEvent(event("evento-1", "AppointmentConfirmed"));

        assertThat(result.outcome()).isEqualTo(EventOutcome.PROCESSED);
        assertThat(result.transition().outcome()).isEqualTo(TransitionOutcome.REJECTED);
        assertThat(recorded().getEventId()).isEqualTo("evento-1");
    }

    // Si el choque de versión lanza, el evento no queda registrado: la transacción se deshace y se repite entero
    @Test
    void aVersionConflictDoesNotRecordTheEvent() {
        processExists();
        when(applyReservationTransition.applyTransitionOnce(eq(ID), any()))
                .thenThrow(new ReservationConflictException(ID, null));
        CatedraEvent event = event("evento-1", "AppointmentConfirmed");

        assertThatThrownBy(() -> useCase.processEvent(event)).isInstanceOf(ReservationConflictException.class);

        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void aProcessThatDisappearedWhileApplyingIsAnError() {
        processExists();
        when(applyReservationTransition.applyTransitionOnce(eq(ID), any())).thenReturn(Optional.empty());
        CatedraEvent event = event("evento-1", "AppointmentConfirmed");

        assertThatThrownBy(() -> useCase.processEvent(event)).isInstanceOf(ReservationProcessNotFoundException.class);

        verify(processedEventRepository, never()).save(any());
    }
}
