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

    // Lo único que publica el relay: el teléfono que el usuario envió (el outbox no guarda el tipo)
    private static final String PHONE_EVENT_TYPE = "AdditionalInformationSubmitted";

    private final TransitionOutcomeLogger transitionOutcomeLogger;

    public void log(List<PhoneDelivery> deliveries) {
        for (PhoneDelivery delivery : deliveries) {
            String eventId = delivery.event().getEventId();
            if (!delivery.sent()) {
                log.atWarn().setMessage("Kafka no confirmó el teléfono, queda pendiente").addKeyValue("eventId", eventId)
                        .addKeyValue("reservationProcessId", delivery.event().getReservationProcessId())
                        .addKeyValue("attempt", delivery.event().getAttempts()).log();
            } else if (delivery.transition().isPresent()) {
                transitionOutcomeLogger.log(PHONE_EVENT_TYPE, eventId, delivery.transition().get().process().getId(),
                        delivery.transition());
            } else {
                log.atInfo().setMessage("El teléfono ya figuraba como enviado").addKeyValue("eventId", eventId).log();
            }
        }
    }
}
