package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class GetReservationUseCaseImplTest {

    @Mock
    private ReservationProcessRepository repository;

    @InjectMocks
    private GetReservationUseCaseImpl useCase;

    @Test
    void returnsTheReservationOfTheUser() {
        ReservationProcess process = ReservationProcess.builder().id(7L).userId("42").build();
        when(repository.findByIdAndUserId(7L, "42")).thenReturn(Optional.of(process));

        assertThat(useCase.getReservation(7L, "42")).containsSame(process);
    }

    // Una ajena y una inexistente son lo mismo para quien consulta: el repositorio no distingue
    @Test
    void aReservationOfAnotherUserIsEmpty() {
        when(repository.findByIdAndUserId(7L, "99")).thenReturn(Optional.empty());

        assertThat(useCase.getReservation(7L, "99")).isEmpty();
    }
}
