package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.List;

import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;
import lombok.extern.slf4j.Slf4j;

// Los casos de uso no loguean (A19): lo hace quien los dispara. Solo ids y estados, nunca datos del paciente
@Slf4j
@Component
public class RecoveryRoundLogger {

    public void log(List<RecoveryResult> results) {
        for (RecoveryResult result : results) {
            LoggingEventBuilder event = switch (result.outcome()) {
                case ORPHAN_FAILED -> log.atWarn().setMessage("Recuperación: el pedido de hold quedó sin "
                        + "resultado conocido, la reserva pasó a FAILED");
                case CONFIRMED_BY_CATEDRA -> log.atWarn().setMessage("Recuperación: la cátedra la tenía "
                        + "confirmada, se repara a CONFIRMED");
                case STILL_OPEN -> log.atInfo().setMessage("Recuperación: la cátedra no la tiene confirmada, "
                        + "se deja como está");
                case UNCHANGED -> log.atInfo().setMessage("Recuperación: otra operación ya había cambiado la "
                        + "reserva");
                case UNVERIFIED -> log.atWarn().setMessage("Recuperación: no se pudo comprobar con la "
                        + "cátedra, no se toca");
                case CONFLICT -> log.atWarn().setMessage("Recuperación: la versión chocó demasiadas veces, "
                        + "no se toca");
            };
            LogFields.round(event, result).log();
        }
        log.atInfo().setMessage("Recuperación al arrancar terminada").addKeyValue("reviewed", results.size()).log();
    }
}
