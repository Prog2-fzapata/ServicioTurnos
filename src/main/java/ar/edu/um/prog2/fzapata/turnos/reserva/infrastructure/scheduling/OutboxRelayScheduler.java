package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.PhoneDeliveryLogger;
import ar.edu.um.prog2.fzapata.turnos.shared.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Adaptador de entrada, como el listener de Kafka: cada cierto tiempo publica los mensajes pendientes del outbox. Es
// una consulta barata (índice parcial sobre los PENDING) y por eso corre siempre (D12). El interruptor, activo por
// defecto, lo apagan los tests para que no publique mientras se prueba otra cosa
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
// MatchIfMissing, si no se definió, default true.
@ConditionalOnProperty(name = "outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRelayScheduler implements SchedulingConfigurer {

    private final ReservaService reservaService;
    private final PhoneDeliveryLogger phoneDeliveryLogger;
    private final OutboxProperties properties;

    // Registrar es la lista de tareas pendientes
    // ConfigureTasks es la función de la interfaz que ejecuta el planificador
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // Runnable, interval e initial delay
        FixedDelayTask task = new FixedDelayTask(this::relay, properties.relayInterval(), properties.relayInterval());
        registrar.addFixedDelayTask(task);
    }

    // Un fallo (la base o Kafka caídos) no debe tumbar el servicio ni cortar las vueltas siguientes: se registra y se
    // reintenta en la próxima
    void relay() {
        try {
            phoneDeliveryLogger.log(reservaService.publishPendingPhones());
        } catch (RuntimeException failure) {
            log.atError().setMessage("El relay del outbox falló; se reintenta en la próxima vuelta")
                    .setCause(failure).log();
        }
    }
}
