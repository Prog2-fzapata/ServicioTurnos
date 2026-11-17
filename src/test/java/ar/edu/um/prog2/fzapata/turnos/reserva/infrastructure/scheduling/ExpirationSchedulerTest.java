package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.ExpirationRoundLogger;
import ar.edu.um.prog2.fzapata.turnos.shared.config.ExpirationProperties;

class ExpirationSchedulerTest {

    private final ReservaService reservaService = mock(ReservaService.class);
    private final ExpirationRoundLogger logger = mock(ExpirationRoundLogger.class);

    private final ExpirationScheduler scheduler = new ExpirationScheduler(reservaService, logger,
            new ExpirationProperties(Duration.ofSeconds(30), Duration.ofSeconds(60), 100));

    @Test
    void registersOneFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        scheduler.configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement().satisfies(task -> {
            assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofSeconds(30));
            assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofSeconds(30));
        });
        assertThat(registrar.getFixedRateTaskList()).isEmpty();
    }

    @Test
    void logsWhatEachRoundDid() {
        List<ExpirationResult> results = List
                .of(new ExpirationResult(7L, ReservationStatus.HELD, ExpirationOutcome.EXPIRED));
        when(reservaService.expireOverdueReservations()).thenReturn(results);

        scheduler.expire();

        verify(logger).log(results);
    }

    // Un fallo (la base caída) no debe cortar las vueltas siguientes: se maneja acá y se reintenta
    @Test
    void handlesAFailureItselfInsteadOfLettingItEscape() {
        when(reservaService.expireOverdueReservations()).thenThrow(new IllegalStateException("la base se cayó"));

        assertThatCode(scheduler::expire).doesNotThrowAnyException();
    }
}
