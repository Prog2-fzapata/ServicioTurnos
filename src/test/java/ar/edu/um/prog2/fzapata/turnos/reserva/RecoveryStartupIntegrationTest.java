package ar.edu.um.prog2.fzapata.turnos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import lombok.RequiredArgsConstructor;

// La recuperación encendida: al terminar de arrancar el contexto corre sola, una vez, contra PostgreSQL real
@SpringBootTest(properties = "recovery.enabled=true")
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class RecoveryStartupIntegrationTest {

    @MockitoSpyBean
    private ReservaService reservaService;

    @Test
    void recoversOnceWhenTheApplicationIsReadyWithTheMomentItStarted() {
        ArgumentCaptor<Instant> startedAt = ArgumentCaptor.forClass(Instant.class);

        verify(reservaService, timeout(15_000).times(1)).recoverReservations(startedAt.capture());

        assertThat(startedAt.getValue()).isBefore(Instant.now());
        verify(reservaService, times(1)).recoverReservations(any());
    }
}
