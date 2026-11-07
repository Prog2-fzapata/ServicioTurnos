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

    // status es el estado en que estaba la reserva cuando se decidió
    private static void logged(LoggingEventBuilder event, RecoveryResult result) {
        event.addKeyValue("reservationId", result.processId()).addKeyValue("status", result.from().name()).log();
    }

    public void log(List<RecoveryResult> results) {
        for (RecoveryResult result : results) {
            switch (result.outcome()) {
                case ORPHAN_FAILED -> logged(log.atWarn().setMessage("Recuperación: el pedido de hold quedó sin "
                        + "resultado conocido, la reserva pasó a FAILED"), result);
                case CONFIRMED_BY_CATEDRA -> logged(log.atWarn().setMessage("Recuperación: la cátedra la tenía "
                        + "confirmada, se repara a CONFIRMED"), result);
                case STILL_OPEN -> logged(log.atInfo().setMessage("Recuperación: la cátedra no la tiene confirmada, "
                        + "se deja como está"), result);
                case UNCHANGED -> logged(log.atInfo().setMessage("Recuperación: otra operación ya había cambiado la "
                        + "reserva"), result);
                case UNVERIFIED -> logged(log.atWarn().setMessage("Recuperación: no se pudo comprobar con la "
                        + "cátedra, no se toca"), result);
                case CONFLICT -> logged(log.atWarn().setMessage("Recuperación: la versión chocó demasiadas veces, "
                        + "no se toca"), result);
            }
        }
        log.atInfo().setMessage("Recuperación al arrancar terminada").addKeyValue("reviewed", results.size()).log();
    }
}
