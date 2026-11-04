package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse.ReservationStreamRegistry;
import ar.edu.um.prog2.fzapata.turnos.shared.config.StreamProperties;

class StreamHeartbeatSchedulerTest {

    private final ReservationStreamRegistry registry = mock(ReservationStreamRegistry.class);

    private final StreamHeartbeatScheduler scheduler = new StreamHeartbeatScheduler(registry,
            new StreamProperties(Duration.ofSeconds(25), 5));

    @Test
    void registersOneFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        scheduler.configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement().satisfies(task -> {
            assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofSeconds(25));
            assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofSeconds(25));
        });
        assertThat(registrar.getFixedRateTaskList()).isEmpty();
    }

    @Test
    void eachRoundSendsTheHeartbeat() {
        scheduler.heartbeat();

        verify(registry).heartbeat();
    }

    // Un fallo no debe cortar las vueltas siguientes: se maneja acá y se repite
    @Test
    void handlesAFailureItselfInsteadOfLettingItEscape() {
        doThrow(new IllegalStateException("falló")).when(registry).heartbeat();

        assertThatCode(scheduler::heartbeat).doesNotThrowAnyException();
    }
}
