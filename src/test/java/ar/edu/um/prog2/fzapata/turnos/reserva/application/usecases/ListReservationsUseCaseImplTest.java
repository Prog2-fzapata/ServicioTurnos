package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationFilter;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class ListReservationsUseCaseImplTest {

    private static final PageQuery PAGE = new PageQuery(0, 20);

    @Mock
    private ReservationProcessRepository repository;

    @InjectMocks
    private ListReservationsUseCaseImpl useCase;

    private PagedResult<ReservationProcess> resultFor(ReservationFilter filter) {
        PagedResult<ReservationProcess> result = new PagedResult<>(List.of(ReservationProcess.builder().id(1L).build()),
                1, PAGE);
        when(repository.search(filter, PAGE)).thenReturn(result);
        return result;
    }

    @Test
    void withoutAStatusItHidesTheDiscardedAttempts() {
        PagedResult<ReservationProcess> expected = resultFor(new ReservationFilter("42", null, true));

        assertThat(useCase.listReservations("42", null, PAGE)).isSameAs(expected);
    }

    @Test
    void withAStatusItFiltersOnlyByThatStatus() {
        PagedResult<ReservationProcess> expected = resultFor(new ReservationFilter("42", ReservationStatus.FAILED, false));

        assertThat(useCase.listReservations("42", ReservationStatus.FAILED, PAGE)).isSameAs(expected);
        verify(repository).search(new ReservationFilter("42", ReservationStatus.FAILED, false), PAGE);
    }
}
