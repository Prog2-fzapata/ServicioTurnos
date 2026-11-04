package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhonePublishException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneDelivery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RelayBatchPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.MarkPhoneSentUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.OutboxRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.PhonePublisher;

@ExtendWith(MockitoExtension.class)
class PublishPendingPhonesUseCaseImplTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private PhonePublisher phonePublisher;

    @Mock
    private MarkPhoneSentUseCase markPhoneSentUseCase;

    private PublishPendingPhonesUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublishPendingPhonesUseCaseImpl(outboxRepository, phonePublisher, markPhoneSentUseCase,
                new RelayBatchPolicy(50), new ConflictRetryPolicy(3));
    }

    private static OutboxEvent pending(long id) {
        return OutboxEvent.builder().id(id).eventId("phone-submitted-" + id).reservationProcessId("process-" + id)
                .topic("catedra.turnos.telefono.fzapata").messageKey("process-" + id).payload("{json " + id + "}")
                .status(OutboxStatus.PENDING).build();
    }

    private static TransitionResult transition() {
        return new TransitionResult(TransitionOutcome.APPLIED, ReservationStatus.PHONE_QUEUED,
                ReservationStatus.PHONE_SUBMITTED, null);
    }

    @Test
    void publishesEachPendingMessageWithItsTopicKeyAndPayloadAndThenMarksItSent() {
        OutboxEvent event = pending(1);
        when(outboxRepository.findPending(50)).thenReturn(List.of(event));
        TransitionResult applied = transition();
        when(markPhoneSentUseCase.markSent(event)).thenReturn(Optional.of(applied));

        List<PhoneDelivery> deliveries = useCase.publishPending();

        assertThat(deliveries).singleElement().satisfies(delivery -> {
            assertThat(delivery.event()).isSameAs(event);
            assertThat(delivery.sent()).isTrue();
            assertThat(delivery.transition()).containsSame(applied);
        });
        InOrder order = inOrder(phonePublisher, markPhoneSentUseCase);
        order.verify(phonePublisher).publish("catedra.turnos.telefono.fzapata", "process-1", "{json 1}");
        order.verify(markPhoneSentUseCase).markSent(event);
    }

    @Test
    void asksForAtMostTheConfiguredBatch() {
        useCase = new PublishPendingPhonesUseCaseImpl(outboxRepository, phonePublisher, markPhoneSentUseCase,
                new RelayBatchPolicy(7), new ConflictRetryPolicy(3));
        when(outboxRepository.findPending(7)).thenReturn(List.of());

        assertThat(useCase.publishPending()).isEmpty();

        verify(outboxRepository).findPending(7);
    }

    @Test
    void publishesTheMessagesInTheOrderTheyCameAndKeepsGoingAfterAFailure() {
        OutboxEvent first = pending(1);
        OutboxEvent second = pending(2);
        when(outboxRepository.findPending(50)).thenReturn(List.of(first, second));
        doThrow(new PhonePublishException("sin ack", null)).when(phonePublisher).publish(any(), eq("process-1"), any());
        when(markPhoneSentUseCase.markSent(second)).thenReturn(Optional.of(transition()));

        List<PhoneDelivery> deliveries = useCase.publishPending();

        assertThat(deliveries).extracting(PhoneDelivery::sent).containsExactly(false, true);
        verify(markPhoneSentUseCase, never()).markSent(first);
    }

    // Sin el ack no se marca nada: el mensaje sigue pendiente, con un intento más, y se reenvía igual
    @Test
    void aMessageKafkaDidNotConfirmStaysPendingWithOneMoreAttempt() {
        OutboxEvent event = pending(1);
        when(outboxRepository.findPending(50)).thenReturn(List.of(event));
        doThrow(new PhonePublishException("sin ack", null)).when(phonePublisher).publish(any(), any(), any());

        List<PhoneDelivery> deliveries = useCase.publishPending();

        assertThat(deliveries).singleElement().satisfies(delivery -> {
            assertThat(delivery.sent()).isFalse();
            assertThat(delivery.transition()).isEmpty();
        });
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(outboxRepository).save(event);
        verify(markPhoneSentUseCase, never()).markSent(any());
    }

    @Test
    void theAttemptsKeepCounting() {
        OutboxEvent event = pending(1);
        event.setAttempts(4);
        when(outboxRepository.findPending(50)).thenReturn(List.of(event));
        doThrow(new PhonePublishException("sin ack", null)).when(phonePublisher).publish(any(), any(), any());

        useCase.publishPending();

        assertThat(event.getAttempts()).isEqualTo(5);
    }

    // Si el proceso cambió mientras tanto se repite solo la actualización: el mensaje ya está publicado y no se reenvía
    @Test
    void aVersionConflictRepeatsOnlyTheUpdateNotThePublication() {
        OutboxEvent event = pending(1);
        when(outboxRepository.findPending(50)).thenReturn(List.of(event));
        when(markPhoneSentUseCase.markSent(event)).thenThrow(new ReservationConflictException(7L, null))
                .thenReturn(Optional.of(transition()));

        List<PhoneDelivery> deliveries = useCase.publishPending();

        assertThat(deliveries).singleElement().satisfies(delivery -> assertThat(delivery.sent()).isTrue());
        verify(phonePublisher, times(1)).publish(any(), any(), any());
        verify(markPhoneSentUseCase, times(2)).markSent(event);
    }

    @Test
    void givesUpOnTheUpdateAfterExactlyTheConfiguredAttempts() {
        OutboxEvent event = pending(1);
        when(outboxRepository.findPending(50)).thenReturn(List.of(event));
        ReservationConflictException conflict = new ReservationConflictException(7L, null);
        when(markPhoneSentUseCase.markSent(event)).thenThrow(conflict);

        assertThatThrownBy(() -> useCase.publishPending()).isSameAs(conflict);

        verify(markPhoneSentUseCase, times(3)).markSent(event);
    }

    @Test
    void anotherFailureWhileMarkingIsNotRetriedAndStopsTheRound() {
        OutboxEvent first = pending(1);
        when(outboxRepository.findPending(50)).thenReturn(List.of(first, pending(2)));
        when(markPhoneSentUseCase.markSent(first)).thenThrow(new IllegalStateException("la base se cayó"));

        assertThatThrownBy(() -> useCase.publishPending()).isInstanceOf(IllegalStateException.class);

        verify(markPhoneSentUseCase, times(1)).markSent(any());
        verify(phonePublisher, times(1)).publish(any(), any(), any());
    }

    @Test
    void withNothingPendingNothingIsPublished() {
        when(outboxRepository.findPending(50)).thenReturn(List.of());

        assertThat(useCase.publishPending()).isEmpty();

        verify(phonePublisher, never()).publish(any(), any(), any());
    }
}
