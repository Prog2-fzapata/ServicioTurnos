package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import lombok.extern.slf4j.Slf4j;

// Los casos de uso no loguean (A19): lo hace quien los dispara. Solo ids y estados, nunca datos del paciente
@Slf4j
@Component
public class ExpirationRoundLogger {

    public void log(List<ExpirationResult> results) {
        for (ExpirationResult result : results) {
            switch (result.outcome()) {
                case EXPIRED -> log.info("Vencimiento: el proceso {} ({}) pasó a EXPIRED", result.processId(),
                        result.from());
                case CONFIRMED_BY_CATEDRA -> log.warn("Vencimiento: la cátedra tenía confirmado el proceso {} ({}); "
                        + "se repara a CONFIRMED en vez de expirarlo", result.processId(), result.from());
                case UNCHANGED -> log.info("Vencimiento: el proceso {} ({}) ya había sido cambiado por otro",
                        result.processId(), result.from());
                case UNVERIFIED -> log.warn("Vencimiento: no se pudo comprobar con la cátedra el proceso {} ({}); "
                        + "no se expira y se reintenta en la próxima vuelta", result.processId(), result.from());
                case CONFLICT -> log.warn("Vencimiento: la versión del proceso {} ({}) chocó demasiadas veces; "
                        + "se reintenta en la próxima vuelta", result.processId(), result.from());
            }
        }
    }
}
