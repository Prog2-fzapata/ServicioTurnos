package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEventType;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionOutcome;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Los casos de uso no loguean (A19): lo hace quien los dispara, sin datos del paciente
@Slf4j
@Component
@RequiredArgsConstructor
public class EventResultLogger {

    private final TransitionOutcomeLogger transitionOutcomeLogger;

    // El pedido de teléfono no cambia el estado: completa los datos de una reserva que ya espera el teléfono, y la app
    // lo ve en canSubmitPhone. Con el mensaje genérico no se entendería qué pasó
    private static boolean isPhoneRequestThatCompletedData(CatedraEvent event, TransitionResult transition) {
        return transition.outcome() == TransitionOutcome.DATA_COMPLETED
                && CatedraEventType.fromWireName(event.eventType())
                        .filter(type -> type == CatedraEventType.ADDITIONAL_INFORMATION_REQUESTED).isPresent();
    }

    public void log(CatedraEvent event, EventResult result) {
        switch (result.outcome()) {
            case PROCESSED -> {
                TransitionResult transition = result.transition();
                Long reservationId = transition.process().getId();
                if (isPhoneRequestThatCompletedData(event, transition)) {
                    LogFields.reservation(LogFields.origin(log.atInfo().setMessage("Pedido de teléfono registrado"),
                            event.eventType(), event.eventId()), reservationId, transition.process().getUserId())
                            .log();
                } else {
                    transitionOutcomeLogger.log(event.eventType(), event.eventId(), reservationId,
                            Optional.of(transition));
                }
            }
            case DUPLICATE -> LogFields.origin(log.atInfo().setMessage("Evento ya procesado, se ignora"),
                    event.eventType(), event.eventId()).log();
            case UNKNOWN_PROCESS -> LogFields.origin(log.atWarn().setMessage("Evento de una reserva desconocida, "
                    + "se ignora"), event.eventType(), event.eventId())
                    .addKeyValue("reservationProcessId", event.reservationProcessId()).log();
            case UNKNOWN_TYPE -> LogFields.origin(log.atWarn().setMessage("Tipo de evento que no se trata, se ignora"),
                    event.eventType(), event.eventId()).log();
        }
    }
}
