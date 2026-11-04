package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.startup;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging.RecoveryRoundLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Adaptador de entrada, como el relay del outbox y el consumer de Kafka: al terminar de arrancar revisa lo que una
// caída pudo dejar dudoso. Corre una vez, en el hilo de arranque (Tomcat ya atiende pedidos). El interruptor, activo
// por defecto, lo apagan los tests para que no toque procesos mientras se prueba otra cosa
@Slf4j
@Component
@ConditionalOnProperty(name = "recovery.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RecoveryRunner {

    private final ReservaService reservaService;
    private final RecoveryRoundLogger recoveryRoundLogger;

    // Un pedido que llega mientras esto corre crea procesos después de que el contexto arrancó: no son huérfanos. Un
    // fallo (la base caída) no debe impedir que el servicio arranque: se registra y sigue
    @EventListener
    void onApplicationReady(ApplicationReadyEvent event) {
        Instant startedAt = Instant.ofEpochMilli(event.getApplicationContext().getStartupDate());
        try {
            recoveryRoundLogger.log(reservaService.recoverReservations(startedAt));
        } catch (RuntimeException failure) {
            log.error("La recuperación al arrancar falló; el servicio sigue funcionando", failure);
        }
    }
}
