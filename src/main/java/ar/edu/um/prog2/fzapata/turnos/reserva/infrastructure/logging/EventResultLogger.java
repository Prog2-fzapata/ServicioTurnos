package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Los casos de uso no loguean (A19): lo hace quien los dispara, sin datos del paciente
@Slf4j
@Component
@RequiredArgsConstructor
public class EventResultLogger {

    private final TransitionOutcomeLogger transitionOutcomeLogger;

    public void log(CatedraEvent event, EventResult result) {
        String origin = "Evento " + event.eventType() + " " + event.eventId();
        switch (result.outcome()) {
            case PROCESSED -> transitionOutcomeLogger.log(origin, result.transition().process().getId(),
                    Optional.of(result.transition()));
            case DUPLICATE -> log.info("{}: ya se había procesado, se ignora", origin);
            case UNKNOWN_PROCESS -> log.warn("{}: ningún proceso local tiene el reservationProcessId {}, se ignora",
                    origin, event.reservationProcessId());
            case UNKNOWN_TYPE -> log.warn("{}: el tipo de evento no se trata, se ignora", origin);
        }
    }
}
