package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.ExpirationRoundLogger;
import ar.edu.um.prog2.fzapata.turnos.shared.config.ExpirationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Adaptador de entrada, como el relay del outbox: cada cierto tiempo expira los procesos vencidos. La consulta es barata
// (índice por estado y vencimiento), así que corre siempre. El interruptor, activo por defecto, lo apagan los tests
// para que no cierre procesos mientras se prueba otra cosa
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "expiration.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ExpirationScheduler implements SchedulingConfigurer {

    private final ReservaService reservaService;
    private final ExpirationRoundLogger expirationRoundLogger;
    private final ExpirationProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // Espera fija entre el fin de una vuelta y el comienzo de la siguiente: dos vueltas nunca se solapan
        registrar.addFixedDelayTask(new FixedDelayTask(this::expire, properties.interval(), properties.interval()));
    }

    // Un fallo (la base caída) no debe cortar las vueltas siguientes: se registra y se reintenta en la próxima
    void expire() {
        try {
            expirationRoundLogger.log(reservaService.expireOverdueReservations());
        } catch (RuntimeException failure) {
            log.atError().setMessage("La tarea de vencimiento falló; se reintenta en la próxima vuelta")
                    .setCause(failure).log();
        }
    }
}
