package ar.edu.um.prog2.fzapata.turnos.reserva.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Availability;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.ApplyReservationTransitionUseCase;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in.GetAvailabilityUseCase;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    private static final AvailabilityQuery QUERY = new AvailabilityQuery(15L, LocalDate.of(2026, 10, 19),
            LocalDate.of(2026, 10, 20));

    @Mock
    private GetAvailabilityUseCase getAvailabilityUseCase;

    @Mock
    private ApplyReservationTransitionUseCase applyReservationTransitionUseCase;

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
}
