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
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneDelivery;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.PhoneDeliveryLogger;
import ar.edu.um.prog2.fzapata.turnos.shared.config.OutboxProperties;

class OutboxRelaySchedulerTest {

    private final ReservaService reservaService = mock(ReservaService.class);
    private final PhoneDeliveryLogger phoneDeliveryLogger = mock(PhoneDeliveryLogger.class);

    private final OutboxRelayScheduler scheduler = new OutboxRelayScheduler(reservaService, phoneDeliveryLogger,
            new OutboxProperties(Duration.ofSeconds(1), 50, Duration.ofSeconds(10)));

    @Test
    void registersOneFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        scheduler.configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement().satisfies(task -> {
            assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofSeconds(1));
            assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofSeconds(1));
        });
        assertThat(registrar.getFixedRateTaskList()).isEmpty();
    }

    @Test
    void logsWhatEachRoundDelivered() {
        List<PhoneDelivery> deliveries = List.of();
        when(reservaService.publishPendingPhones()).thenReturn(deliveries);

        scheduler.relay();

        verify(phoneDeliveryLogger).log(deliveries);
    }

    // Un fallo (la base o Kafka caídos) no debe cortar las vueltas siguientes: se maneja acá y se reintenta
    @Test
    void handlesAFailureItselfInsteadOfLettingItEscape() {
        when(reservaService.publishPendingPhones()).thenThrow(new IllegalStateException("la base se cayó"));

        assertThatCode(scheduler::relay).doesNotThrowAnyException();
    }
}
