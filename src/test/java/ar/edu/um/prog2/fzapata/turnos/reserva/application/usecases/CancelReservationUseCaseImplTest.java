package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationNotCancellableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.CatedraApiClient;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class CancelReservationUseCaseImplTest {

    private static final Long ID = 7L;
    private static final String USER = "42";

    @Mock
    private ReservationProcessRepository repository;

    @Mock
    private CatedraApiClient catedraApiClient;

    @Mock
    private ApplyReservationTransitionUseCase applyTransition;

    @InjectMocks
    private CancelReservationUseCaseImpl useCase;

    private static ReservationProcess process(ReservationStatus status) {
        return ReservationProcess.builder().id(ID).userId(USER).reservationProcessId("process-1").status(status)
                .build();
    }

    private void owns(ReservationProcess process) {
        when(repository.findByIdAndUserId(ID, USER)).thenReturn(Optional.of(process));
    }

    private void transitionReturns(TransitionOutcome outcome, ReservationStatus from, ReservationProcess process) {
        when(applyTransition.applyTransition(any(), any())).thenReturn(
                Optional.of(new TransitionResult(outcome, from, ReservationStatus.CANCELLED, process)));
    }

    private CatedraRejectedException rejected(CatedraRejection rejection) {
        return new CatedraRejectedException(rejection);
    }

    @Test
    void cancelsInTheCatedraAndThenMarksTheProcessAsCancelled() {
        owns(process(ReservationStatus.CONFIRMED));
        ReservationProcess cancelled = process(ReservationStatus.CANCELLED);
        transitionReturns(TransitionOutcome.APPLIED, ReservationStatus.CONFIRMED, cancelled);

        assertThat(useCase.cancelReservation(ID, USER, "no puedo ir")).containsSame(cancelled);

        verify(catedraApiClient).cancelAppointment("process-1", "no puedo ir");
        ArgumentCaptor<ReservationTransition> captor = ArgumentCaptor.forClass(ReservationTransition.class);
        verify(applyTransition).applyTransition(eq(ID), captor.capture());
        assertThat(captor.getValue().target()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(captor.getValue().cancellationReason()).isEqualTo("no puedo ir");
    }

    @Test
    void theReasonIsOptional() {
        owns(process(ReservationStatus.CONFIRMED));
        transitionReturns(TransitionOutcome.APPLIED, ReservationStatus.CONFIRMED, process(ReservationStatus.CANCELLED));

        useCase.cancelReservation(ID, USER, null);

        verify(catedraApiClient).cancelAppointment("process-1", null);
    }

    @Test
    void aReservationOfAnotherUserIsEmptyAndNobodyIsCalled() {
        when(repository.findByIdAndUserId(ID, USER)).thenReturn(Optional.empty());

        assertThat(useCase.cancelReservation(ID, USER, null)).isEmpty();

        verifyNoInteractions(catedraApiClient, applyTransition);
    }

    // Idempotencia: repetir la cancelación devuelve el estado actual sin tocar la cátedra
    @Test
    void anAlreadyCancelledReservationIsReturnedAsIsWithoutCallingAnyone() {
        ReservationProcess cancelled = process(ReservationStatus.CANCELLED);
        owns(cancelled);

        assertThat(useCase.cancelReservation(ID, USER, "otra vez")).containsSame(cancelled);

        verifyNoInteractions(catedraApiClient, applyTransition);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"CONFIRMED", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void anyOtherStatusIsNotCancellableAndNobodyIsCalled(ReservationStatus status) {
        owns(process(status));

        assertThatThrownBy(() -> useCase.cancelReservation(ID, USER, null))
                .isInstanceOf(ReservationNotCancellableException.class).hasMessageContaining(status.name());

        verifyNoInteractions(catedraApiClient, applyTransition);
    }

    @Test
    void ifTheCatedraCannotCancelItTheProcessDoesNotChange() {
        owns(process(ReservationStatus.CONFIRMED));
        when(catedraApiClient.cancelAppointment("process-1", null))
                .thenThrow(rejected(CatedraRejection.RESERVATION_NOT_CANCELLABLE));

        assertThatThrownBy(() -> useCase.cancelReservation(ID, USER, null))
                .isInstanceOf(ReservationNotCancellableException.class);

        verify(applyTransition, never()).applyTransition(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = CatedraRejection.class, names = {"RESERVATION_NOT_FOUND", "SLOT_ALREADY_HELD", "HOLD_EXPIRED"})
    void anyOtherRejectionIsAMismatchBetweenTheServices(CatedraRejection rejection) {
        owns(process(ReservationStatus.CONFIRMED));
        when(catedraApiClient.cancelAppointment("process-1", null)).thenThrow(rejected(rejection));

        assertThatThrownBy(() -> useCase.cancelReservation(ID, USER, null))
                .isInstanceOf(CatedraResponseException.class).hasMessageContaining(rejection.name());

        verify(applyTransition, never()).applyTransition(any(), any());
    }

    @Test
    void ifTheCatedraIsDownTheFailureReachesTheCallerAndTheProcessDoesNotChange() {
        owns(process(ReservationStatus.CONFIRMED));
        CatedraUnavailableException down = new CatedraUnavailableException("caída", null);
        when(catedraApiClient.cancelAppointment("process-1", null)).thenThrow(down);

        assertThatThrownBy(() -> useCase.cancelReservation(ID, USER, null)).isSameAs(down);

        verify(applyTransition, never()).applyTransition(any(), any());
    }

    @Test
    void ifAnotherCancellationGotThereFirstTheResultIsTheSame() {
        owns(process(ReservationStatus.CONFIRMED));
        ReservationProcess cancelled = process(ReservationStatus.CANCELLED);
        transitionReturns(TransitionOutcome.ALREADY_IN_STATE, ReservationStatus.CANCELLED, cancelled);

        assertThat(useCase.cancelReservation(ID, USER, null)).containsSame(cancelled);
    }

    @Test
    void ifTheProcessMovedOnWhileCancellingItIsNotCancellable() {
        owns(process(ReservationStatus.CONFIRMED));
        transitionReturns(TransitionOutcome.REJECTED, ReservationStatus.FAILED, process(ReservationStatus.FAILED));

        assertThatThrownBy(() -> useCase.cancelReservation(ID, USER, null))
                .isInstanceOf(ReservationNotCancellableException.class).hasMessageContaining("FAILED");
    }

    @Test
    void aProcessThatDisappearedWhileCancellingIsNotFound() {
        owns(process(ReservationStatus.CONFIRMED));
        when(applyTransition.applyTransition(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.cancelReservation(ID, USER, null))
                .isInstanceOf(ReservationProcessNotFoundException.class);
    }
}
