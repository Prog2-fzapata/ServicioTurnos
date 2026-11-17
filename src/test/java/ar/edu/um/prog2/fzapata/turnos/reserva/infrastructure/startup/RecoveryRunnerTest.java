package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.RecoveryRoundLogger;

class RecoveryRunnerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-04T14:00:00Z");

    private final ReservaService reservaService = mock(ReservaService.class);
    private final RecoveryRoundLogger logger = mock(RecoveryRoundLogger.class);
    private final RecoveryRunner runner = new RecoveryRunner(reservaService, logger);
    private final ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);

    private void contextStartedAt(Instant startedAt) {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getStartupDate()).thenReturn(startedAt.toEpochMilli());
        when(event.getApplicationContext()).thenReturn(context);
    }

    // La guarda: lo creado después de que arrancó el contexto es un pedido en curso de esta instancia, no un huérfano
    @Test
    void recoversWithTheMomentTheContextStarted() {
        contextStartedAt(STARTED_AT);
        List<RecoveryResult> results = List
                .of(new RecoveryResult(1L, ReservationStatus.HOLD_REQUESTED, RecoveryOutcome.ORPHAN_FAILED));
        when(reservaService.recoverReservations(STARTED_AT)).thenReturn(results);

        runner.onApplicationReady(event);

        verify(logger).log(results);
    }

    // Un fallo (la base caída) no debe impedir que el servicio arranque
    @Test
    void aFailureNeverStopsTheService() {
        contextStartedAt(STARTED_AT);
        when(reservaService.recoverReservations(any())).thenThrow(new IllegalStateException("la base se cayó"));

        assertThatCode(() -> runner.onApplicationReady(event)).doesNotThrowAnyException();
    }
}
