package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Los casos de uso no loguean (A19): lo hace quien los dispara. Nunca incluye el teléfono ni el contenido del mensaje,
// solo ids, estados y el número de intento
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneDeliveryLogger {

    private final TransitionOutcomeLogger transitionOutcomeLogger;

    public void log(List<PhoneDelivery> deliveries) {
        for (PhoneDelivery delivery : deliveries) {
            String eventId = delivery.event().getEventId();
            if (!delivery.sent()) {
                log.warn("Teléfono {}: Kafka no confirmó el mensaje del proceso {}, queda pendiente (intento {})",
                        eventId, delivery.event().getReservationProcessId(), delivery.event().getAttempts());
            } else if (delivery.transition().isPresent()) {
                transitionOutcomeLogger.log("Teléfono " + eventId, delivery.transition().get().process().getId(),
                        delivery.transition());
            } else {
                log.info("Teléfono {}: ya figuraba como enviado", eventId);
            }
        }
    }
}
