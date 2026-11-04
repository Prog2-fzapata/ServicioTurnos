package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;
import lombok.extern.slf4j.Slf4j;

// Los casos de uso no loguean (A19): lo hace quien los dispara. Solo ids y estados, nunca datos del paciente
@Slf4j
@Component
public class RecoveryRoundLogger {

    public void log(List<RecoveryResult> results) {
        for (RecoveryResult result : results) {
            switch (result.outcome()) {
                case ORPHAN_FAILED -> log.warn("Recuperación: el pedido de hold del proceso {} quedó sin resultado "
                        + "conocido; pasó a FAILED", result.processId());
                case CONFIRMED_BY_CATEDRA -> log.warn("Recuperación: la cátedra tenía confirmado el proceso {} ({}); "
                        + "se repara a CONFIRMED", result.processId(), result.from());
                case STILL_OPEN -> log.info("Recuperación: la cátedra no tiene confirmado el proceso {} ({}); "
                        + "se deja como está", result.processId(), result.from());
                case UNCHANGED -> log.info("Recuperación: el proceso {} ({}) ya había sido cambiado por otro",
                        result.processId(), result.from());
                case UNVERIFIED -> log.warn("Recuperación: no se pudo comprobar con la cátedra el proceso {} ({}); "
                        + "no se toca", result.processId(), result.from());
                case CONFLICT -> log.warn("Recuperación: la versión del proceso {} ({}) chocó demasiadas veces; "
                        + "no se toca", result.processId(), result.from());
            }
        }
        log.info("Recuperación al arrancar terminada: {} procesos revisados", results.size());
    }
}
