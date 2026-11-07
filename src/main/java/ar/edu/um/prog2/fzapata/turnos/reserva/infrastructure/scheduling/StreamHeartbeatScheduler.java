package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse.ReservationStreamRegistry;
import ar.edu.um.prog2.fzapata.turnos.shared.config.StreamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Adaptador de entrada, como el relay del outbox: cada cierto tiempo manda el latido a las conexiones del stream y
// cierra las de JWT vencido. No tiene interruptor porque sin conexiones abiertas no hace nada
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
public class StreamHeartbeatScheduler implements SchedulingConfigurer {

    private final ReservationStreamRegistry registry;
    private final StreamProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(this::heartbeat, properties.heartbeatInterval(),
                properties.heartbeatInterval()));
    }

    // Un fallo no debe cortar las vueltas siguientes: se registra y se repite en la próxima
    void heartbeat() {
        try {
            registry.heartbeat();
        } catch (RuntimeException failure) {
            log.atError().setMessage("El latido del stream falló; se repite en la próxima vuelta")
                    .setCause(failure).log();
        }
    }
}
