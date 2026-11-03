package ar.edu.um.prog2.fzapata.turnos.reserva.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ConflictRetryPolicy;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneDelivery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.CancelReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetAvailabilityUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetReservationUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ListReservationsUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ProcessCatedraEventUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.PublishPendingPhonesUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.SubmitPhoneUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.StartReservationUseCase;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    private static final AvailabilityQuery QUERY = new AvailabilityQuery(15L, LocalDate.of(2026, 10, 19),
            LocalDate.of(2026, 10, 20));

    @Mock
    private GetAvailabilityUseCase getAvailabilityUseCase;

    @Mock
    private ApplyReservationTransitionUseCase applyReservationTransitionUseCase;

    @Mock
    private StartReservationUseCase startReservationUseCase;

    @Mock
    private ListReservationsUseCase listReservationsUseCase;

    @Mock
    private GetReservationUseCase getReservationUseCase;

    @Mock
    private CancelReservationUseCase cancelReservationUseCase;

    @Mock
    private ProcessCatedraEventUseCase processCatedraEventUseCase;

    @Mock
    private SubmitPhoneUseCase submitPhoneUseCase;

    @Mock
    private PublishPendingPhonesUseCase publishPendingPhonesUseCase;

    @Spy
    private ConflictRetryPolicy conflictRetryPolicy = new ConflictRetryPolicy(3);

    @InjectMocks
    private ReservaService reservaService;

    @Test
    void returnsTheAvailabilityOfTheUseCase() {
        Availability availability = Availability.builder().professionalId(15L).slots(List.of()).build();
        when(getAvailabilityUseCase.getAvailability(QUERY)).thenReturn(Optional.of(availability));

        assertThat(reservaService.getAvailability(QUERY)).isSameAs(availability);
    }

    // Es el service, y no el caso de uso, quien convierte la ausencia en una excepción de negocio
    @Test
    void anEmptyAnswerBecomesAProfessionalNotFound() {
        when(getAvailabilityUseCase.getAvailability(QUERY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.getAvailability(QUERY))
                .isInstanceOf(ProfessionalNotFoundException.class).hasMessageContaining("15");
    }

    @Test
    void returnsTheResultOfTheTransition() {
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.WAITING_PHONE);
        TransitionResult result = new TransitionResult(TransitionOutcome.APPLIED, ReservationStatus.HELD,
                ReservationStatus.WAITING_PHONE, null);
        when(applyReservationTransitionUseCase.applyTransition(7L, transition)).thenReturn(Optional.of(result));

        assertThat(reservaService.applyTransition(7L, transition)).isSameAs(result);
    }

    @Test
    void aTransitionOfAMissingProcessBecomesAProcessNotFound() {
        ReservationTransition transition = ReservationTransition.to(ReservationStatus.WAITING_PHONE);
        when(applyReservationTransitionUseCase.applyTransition(7L, transition)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.applyTransition(7L, transition))
                .isInstanceOf(ReservationProcessNotFoundException.class).hasMessageContaining("7");
    }

    @Test
    void returnsTheReservationThatWasStarted() {
        ReservationRequest request = new ReservationRequest("42", 15L, LocalDate.of(2026, 10, 19),
                LocalTime.of(9, 30));
        ReservationProcess process = ReservationProcess.builder().id(1L).status(ReservationStatus.WAITING_PHONE)
                .build();
        when(startReservationUseCase.startReservation(request)).thenReturn(Optional.of(process));

        assertThat(reservaService.startReservation(request)).isSameAs(process);
    }

    @Test
    void startingAReservationWithAMissingProfessionalBecomesAProfessionalNotFound() {
        ReservationRequest request = new ReservationRequest("42", 99L, LocalDate.of(2026, 10, 19),
                LocalTime.of(9, 30));
        when(startReservationUseCase.startReservation(request)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.startReservation(request))
                .isInstanceOf(ProfessionalNotFoundException.class).hasMessageContaining("99");
    }

    @Test
    void returnsThePageOfReservationsOfTheUseCase() {
        PageQuery page = new PageQuery(0, 20);
        PagedResult<ReservationProcess> result = new PagedResult<>(List.of(), 0, page);
        when(listReservationsUseCase.listReservations("42", ReservationStatus.CONFIRMED, page)).thenReturn(result);

        assertThat(reservaService.listReservations("42", ReservationStatus.CONFIRMED, page)).isSameAs(result);
    }

    @Test
    void returnsTheReservationOfTheUser() {
        ReservationProcess process = ReservationProcess.builder().id(7L).build();
        when(getReservationUseCase.getReservation(7L, "42")).thenReturn(Optional.of(process));

        assertThat(reservaService.getReservation(7L, "42")).isSameAs(process);
    }

    @Test
    void aMissingOrForeignReservationBecomesAProcessNotFound() {
        when(getReservationUseCase.getReservation(7L, "42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.getReservation(7L, "42"))
                .isInstanceOf(ReservationProcessNotFoundException.class).hasMessageContaining("7");
    }

    @Test
    void returnsTheReservationThatWasCancelled() {
        ReservationProcess process = ReservationProcess.builder().id(7L).status(ReservationStatus.CANCELLED).build();
        when(cancelReservationUseCase.cancelReservation(7L, "42", "motivo")).thenReturn(Optional.of(process));

        assertThat(reservaService.cancelReservation(7L, "42", "motivo")).isSameAs(process);
    }

    @Test
    void cancellingAMissingOrForeignReservationBecomesAProcessNotFound() {
        when(cancelReservationUseCase.cancelReservation(7L, "42", null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.cancelReservation(7L, "42", null))
                .isInstanceOf(ReservationProcessNotFoundException.class).hasMessageContaining("7");
    }

    private static final CatedraEvent EVENT = new CatedraEvent("evento-1", "AppointmentConfirmed", "process-1", 8251L,
            null, null);

    @Test
    void returnsTheResultOfProcessingTheEvent() {
        EventResult result = EventResult.without(EventOutcome.DUPLICATE);
        when(processCatedraEventUseCase.processEvent(EVENT)).thenReturn(result);

        assertThat(reservaService.processEvent(EVENT)).isSameAs(result);
    }

    // El reintento vive acá, fuera de la transacción del caso de uso: cada intento es una transacción nueva
    @Test
    void aVersionConflictRepeatsTheWholeEvent() {
        EventResult result = EventResult.without(EventOutcome.UNKNOWN_PROCESS);
        when(processCatedraEventUseCase.processEvent(EVENT)).thenThrow(new ReservationConflictException(7L, null))
                .thenThrow(new ReservationConflictException(7L, null)).thenReturn(result);

        assertThat(reservaService.processEvent(EVENT)).isSameAs(result);
        verify(processCatedraEventUseCase, times(3)).processEvent(EVENT);
    }

    @Test
    void givesUpAfterExactlyTheConfiguredAttempts() {
        ReservationConflictException conflict = new ReservationConflictException(7L, null);
        when(processCatedraEventUseCase.processEvent(EVENT)).thenThrow(conflict);

        assertThatThrownBy(() -> reservaService.processEvent(EVENT)).isSameAs(conflict);
        verify(processCatedraEventUseCase, times(3)).processEvent(EVENT);
    }

    @Test
    void anyOtherFailureIsNotRetried() {
        when(processCatedraEventUseCase.processEvent(EVENT)).thenThrow(new IllegalStateException("la base se cayó"));

        assertThatThrownBy(() -> reservaService.processEvent(EVENT)).isInstanceOf(IllegalStateException.class);
        verify(processCatedraEventUseCase, times(1)).processEvent(EVENT);
    }

    @Test
    void returnsTheReservationWithThePhoneQueued() {
        ReservationProcess queued = ReservationProcess.builder().id(7L).status(ReservationStatus.PHONE_QUEUED).build();
        when(submitPhoneUseCase.submitPhone(7L, "42", "2616159550")).thenReturn(Optional.of(queued));

        assertThat(reservaService.submitPhone(7L, "42", "2616159550")).isSameAs(queued);
    }

    @Test
    void submittingThePhoneOfAMissingOrForeignReservationBecomesAProcessNotFound() {
        when(submitPhoneUseCase.submitPhone(7L, "42", "2616159550")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.submitPhone(7L, "42", "2616159550"))
                .isInstanceOf(ReservationProcessNotFoundException.class).hasMessageContaining("7");
    }

    // Cada intento es una transacción nueva del caso de uso
    @Test
    void aVersionConflictRepeatsTheSubmission() {
        ReservationProcess queued = ReservationProcess.builder().id(7L).status(ReservationStatus.PHONE_QUEUED).build();
        when(submitPhoneUseCase.submitPhone(7L, "42", "2616159550"))
                .thenThrow(new ReservationConflictException(7L, null)).thenReturn(Optional.of(queued));

        assertThat(reservaService.submitPhone(7L, "42", "2616159550")).isSameAs(queued);
        verify(submitPhoneUseCase, times(2)).submitPhone(7L, "42", "2616159550");
    }

    @Test
    void givesUpOnTheSubmissionAfterExactlyTheConfiguredAttempts() {
        ReservationConflictException conflict = new ReservationConflictException(7L, null);
        when(submitPhoneUseCase.submitPhone(7L, "42", "2616159550")).thenThrow(conflict);

        assertThatThrownBy(() -> reservaService.submitPhone(7L, "42", "2616159550")).isSameAs(conflict);
        verify(submitPhoneUseCase, times(3)).submitPhone(7L, "42", "2616159550");
    }

    @Test
    void aBusinessErrorWhileSubmittingIsNotRetried() {
        when(submitPhoneUseCase.submitPhone(7L, "42", "2616159550"))
                .thenThrow(new IllegalStateException("estado inválido"));

        assertThatThrownBy(() -> reservaService.submitPhone(7L, "42", "2616159550"))
                .isInstanceOf(IllegalStateException.class);
        verify(submitPhoneUseCase, times(1)).submitPhone(7L, "42", "2616159550");
    }

    @Test
    void returnsWhatTheRelayDelivered() {
        List<PhoneDelivery> deliveries = List.of();
        when(publishPendingPhonesUseCase.publishPending()).thenReturn(deliveries);

        assertThat(reservaService.publishPendingPhones()).isSameAs(deliveries);
    }
}
