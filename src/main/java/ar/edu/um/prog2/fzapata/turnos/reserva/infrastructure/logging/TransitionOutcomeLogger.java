package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;
import lombok.extern.slf4j.Slf4j;

// Los casos de uso no loguean (A19): quien los dispara registra el resultado, y lo hace siempre igual.
// Nunca incluye datos del paciente ni del profesional, solo ids y estados
@Slf4j
@Component
public class TransitionOutcomeLogger {

    // origin dice qué disparó el cambio, por ejemplo "Evento AppointmentConfirmed"
    public void log(String origin, Long processId, Optional<TransitionResult> result) {
        if (result.isEmpty()) {
            log.warn("{}: el proceso de reserva {} no existe", origin, processId);
            return;
        }
        TransitionResult transition = result.get();
        switch (transition.outcome()) {
            case APPLIED -> log.info("{}: el proceso {} pasó de {} a {}", origin, processId, transition.from(),
                    transition.target());
            case DATA_COMPLETED -> log.info("{}: el proceso {} ya estaba en {}, se completaron datos que faltaban",
                    origin, processId, transition.target());
            case ALREADY_IN_STATE -> log.info("{}: el proceso {} ya estaba en {}, sin cambios", origin, processId,
                    transition.target());
            case REJECTED -> log.warn("{}: se ignora el paso de {} a {} del proceso {}: el diagrama no lo permite",
                    origin, transition.from(), transition.target(), processId);
        }
    }
}
