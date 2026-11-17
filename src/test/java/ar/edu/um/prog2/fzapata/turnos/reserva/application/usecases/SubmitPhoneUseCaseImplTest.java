package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidReservationStateException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhoneWindowExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneMessageSettings;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneSubmission;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.OutboxRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.PhoneMessageSerializer;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class SubmitPhoneUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-04T13:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-04T13:09:00Z");
    private static final Long ID = 7L;
    private static final String USER = "42";

    @Mock
    private ReservationProcessRepository reservationProcessRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ApplyReservationTransitionUseCase applyReservationTransition;

    @Mock
    private PhoneMessageSerializer phoneMessageSerializer;

    private SubmitPhoneUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new SubmitPhoneUseCaseImpl(reservationProcessRepository, outboxRepository,
                applyReservationTransition, phoneMessageSerializer,
                new PhoneMessageSettings("catedra.turnos.telefono.fzapata", "fzapata"),
                Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private static ReservationProcess process(ReservationStatus status, String requestEventId, Instant expiresAt) {
        return ReservationProcess.builder().id(ID).userId(USER).reservationProcessId("process-1").holdId("hold-1")
                .status(status).requestEventId(requestEventId).expiresAt(expiresAt).build();
    }

    private ReservationProcess waiting() {
        return process(ReservationStatus.WAITING_PHONE, "request-event-1", EXPIRES_AT);
    }

    private void owns(ReservationProcess process) {
        when(reservationProcessRepository.findByIdAndUserId(ID, USER)).thenReturn(Optional.of(process));
    }

    private ReservationProcess queuedWith(String phone) {
        return ReservationProcess.builder().id(ID).userId(USER).reservationProcessId("process-1").holdId("hold-1")
                .status(ReservationStatus.PHONE_QUEUED).requestEventId("request-event-1").patientPhone(phone).build();
    }

    private void transitionApplies(ReservationProcess queued) {
        when(applyReservationTransition.applyTransitionOnce(any(), any())).thenReturn(Optional.of(
                new TransitionResult(TransitionOutcome.APPLIED, ReservationStatus.WAITING_PHONE,
                        ReservationStatus.PHONE_QUEUED, queued)));
    }

    private OutboxEvent savedEvent() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void queuesThePhoneWithTheNormalizedNumberAndSavesTheMessage() {
        owns(waiting());
        ReservationProcess queued = queuedWith("+5492616159550");
        transitionApplies(queued);
        when(phoneMessageSerializer.toPayload(any())).thenReturn("{json}");

        Optional<ReservationProcess> result = useCase.submitPhone(ID, USER, "+54 9 261 615-9550");

        assertThat(result).containsSame(queued);
        ArgumentCaptor<ReservationTransition> transition = ArgumentCaptor.forClass(ReservationTransition.class);
        verify(applyReservationTransition).applyTransitionOnce(eq(ID), transition.capture());
        assertThat(transition.getValue().target()).isEqualTo(ReservationStatus.PHONE_QUEUED);
        assertThat(transition.getValue().patientPhone()).isEqualTo("+5492616159550");
        OutboxEvent event = savedEvent();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getTopic()).isEqualTo("catedra.turnos.telefono.fzapata");
        assertThat(event.getMessageKey()).isEqualTo("process-1");
        assertThat(event.getReservationProcessId()).isEqualTo("process-1");
        assertThat(event.getPayload()).isEqualTo("{json}");
        assertThat(event.getCreatedAt()).isEqualTo(NOW);
        assertThat(event.getEventId()).startsWith("phone-submitted-");
    }

    // El mensaje responde al pedido de la cátedra: lleva su requestEventId, el hold y el número ya normalizado
    @Test
    void theMessageAnswersTheRequestOfTheCatedraWithTheEventIdOfTheOutboxRow() {
        owns(waiting());
        transitionApplies(queuedWith("2616159550"));
        when(phoneMessageSerializer.toPayload(any())).thenReturn("{json}");

        useCase.submitPhone(ID, USER, "(261) 615-9550");

        ArgumentCaptor<PhoneSubmission> submission = ArgumentCaptor.forClass(PhoneSubmission.class);
        verify(phoneMessageSerializer).toPayload(submission.capture());
        assertThat(submission.getValue().eventId()).isEqualTo(savedEvent().getEventId());
        assertThat(submission.getValue().reservationProcessId()).isEqualTo("process-1");
        assertThat(submission.getValue().holdId()).isEqualTo("hold-1");
        assertThat(submission.getValue().requestEventId()).isEqualTo("request-event-1");
        assertThat(submission.getValue().phoneNumber()).isEqualTo("2616159550");
        assertThat(submission.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void everyMessageGetsItsOwnEventId() {
        owns(waiting());
        transitionApplies(queuedWith("2616159550"));
        when(phoneMessageSerializer.toPayload(any())).thenReturn("{json}");

        useCase.submitPhone(ID, USER, "2616159550");
        useCase.submitPhone(ID, USER, "2616159550");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(OutboxEvent::getEventId).doesNotHaveDuplicates();
    }

    @Test
    void usesTheSingleAttemptBecauseItRunsInsideATransaction() {
        owns(waiting());
        transitionApplies(queuedWith("2616159550"));
        when(phoneMessageSerializer.toPayload(any())).thenReturn("{json}");

        useCase.submitPhone(ID, USER, "2616159550");

        verify(applyReservationTransition, never()).applyTransition(any(), any());
    }

    @Test
    void aReservationOfAnotherUserIsEmptyAndNothingHappens() {
        when(reservationProcessRepository.findByIdAndUserId(ID, USER)).thenReturn(Optional.empty());

        assertThat(useCase.submitPhone(ID, USER, "2616159550")).isEmpty();

        verifyNoInteractions(applyReservationTransition, outboxRepository, phoneMessageSerializer);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = "WAITING_PHONE", mode = EnumSource.Mode.EXCLUDE)
    void anyStatusOtherThanWaitingForThePhoneIsRejected(ReservationStatus status) {
        owns(process(status, "request-event-1", EXPIRES_AT));

        assertThatThrownBy(() -> useCase.submitPhone(ID, USER, "2616159550"))
                .isInstanceOf(InvalidReservationStateException.class).hasMessageContaining(status.name());

        verifyNoInteractions(applyReservationTransition, outboxRepository);
    }

    // Todavía no llegó el pedido de la cátedra
    @Test
    void itIsRejectedWhileTheCatedraHasNotAskedForThePhone() {
        owns(process(ReservationStatus.WAITING_PHONE, null, EXPIRES_AT));

        assertThatThrownBy(() -> useCase.submitPhone(ID, USER, "2616159550"))
                .isInstanceOf(InvalidReservationStateException.class);

        verifyNoInteractions(applyReservationTransition, outboxRepository);
    }

    @Test
    void itIsRejectedOnceTheExpiryHasPassed() {
        owns(process(ReservationStatus.WAITING_PHONE, "request-event-1", NOW.minusSeconds(1)));

        assertThatThrownBy(() -> useCase.submitPhone(ID, USER, "2616159550"))
                .isInstanceOf(PhoneWindowExpiredException.class);

        verifyNoInteractions(applyReservationTransition, outboxRepository);
    }

    // El vencimiento es exclusivo: en el mismo instante ya no se acepta
    @Test
    void itIsRejectedExactlyAtTheExpiry() {
        owns(process(ReservationStatus.WAITING_PHONE, "request-event-1", NOW));

        assertThatThrownBy(() -> useCase.submitPhone(ID, USER, "2616159550"))
                .isInstanceOf(PhoneWindowExpiredException.class);
    }

    @Test
    void itIsAcceptedOneSecondBeforeTheExpiry() {
        owns(process(ReservationStatus.WAITING_PHONE, "request-event-1", NOW.plusSeconds(1)));
        transitionApplies(queuedWith("2616159550"));
        when(phoneMessageSerializer.toPayload(any())).thenReturn("{json}");

        assertThat(useCase.submitPhone(ID, USER, "2616159550")).isPresent();
    }

    @Test
    void aProcessWithoutExpiryIsAccepted() {
        owns(process(ReservationStatus.WAITING_PHONE, "request-event-1", null));
        transitionApplies(queuedWith("2616159550"));
        when(phoneMessageSerializer.toPayload(any())).thenReturn("{json}");

        assertThat(useCase.submitPhone(ID, USER, "2616159550")).isPresent();
    }

    // Otro cambio se adelantó entre la lectura y el paso (por ejemplo, el vencimiento): el paso ya no corresponde
    @ParameterizedTest
    @EnumSource(value = TransitionOutcome.class, names = "APPLIED", mode = EnumSource.Mode.EXCLUDE)
    void ifTheStepIsNotAppliedNothingIsSaved(TransitionOutcome outcome) {
        owns(waiting());
        when(applyReservationTransition.applyTransitionOnce(any(), any())).thenReturn(Optional.of(
                new TransitionResult(outcome, ReservationStatus.EXPIRED, ReservationStatus.PHONE_QUEUED, null)));

        assertThatThrownBy(() -> useCase.submitPhone(ID, USER, "2616159550"))
                .isInstanceOf(InvalidReservationStateException.class).hasMessageContaining("EXPIRED");

        verifyNoInteractions(outboxRepository);
    }

    @Test
    void aProcessThatDisappearedWhileApplyingIsNotFound() {
        owns(waiting());
        when(applyReservationTransition.applyTransitionOnce(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.submitPhone(ID, USER, "2616159550"))
                .isInstanceOf(ReservationProcessNotFoundException.class);

        verifyNoInteractions(outboxRepository);
    }

    // El choque de versión lo repite quien llama con una transacción nueva: acá no se guarda nada
    @Test
    void aVersionConflictPropagatesWithoutSavingTheMessage() {
        owns(waiting());
        when(applyReservationTransition.applyTransitionOnce(any(), any()))
                .thenThrow(new ReservationConflictException(ID, null));

        assertThatThrownBy(() -> useCase.submitPhone(ID, USER, "2616159550"))
                .isInstanceOf(ReservationConflictException.class);

        verifyNoInteractions(outboxRepository);
    }

    // Defensa: el controller ya valida, pero este caso de uso no confía en que lo hayan hecho
    @Test
    void anInvalidNumberNeverReachesTheTransition() {
        owns(waiting());

        assertThatThrownBy(() -> useCase.submitPhone(ID, USER, "123"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(applyReservationTransition, outboxRepository);
    }
}
