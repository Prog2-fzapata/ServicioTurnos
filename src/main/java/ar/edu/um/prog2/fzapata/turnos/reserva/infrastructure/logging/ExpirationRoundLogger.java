package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import java.util.List;

import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;
import lombok.extern.slf4j.Slf4j;

// Los casos de uso no loguean (A19): lo hace quien los dispara. Solo ids y estados, nunca datos del paciente
@Slf4j
@Component
public class ExpirationRoundLogger {

    public void log(List<ExpirationResult> results) {
        for (ExpirationResult result : results) {
            LoggingEventBuilder event = switch (result.outcome()) {
                case EXPIRED -> log.atInfo().setMessage("Vencimiento: reserva expirada");
                case CONFIRMED_BY_CATEDRA -> log.atWarn().setMessage("Vencimiento: la cátedra la tenía "
                        + "confirmada, se repara a CONFIRMED en vez de expirarla");
                case UNCHANGED -> log.atInfo().setMessage("Vencimiento: otra operación ya había cambiado la "
                        + "reserva");
                case UNVERIFIED -> log.atWarn().setMessage("Vencimiento: no se pudo comprobar con la cátedra, "
                        + "no se expira y se reintenta en la próxima vuelta");
                case CONFLICT -> log.atWarn().setMessage("Vencimiento: la versión chocó demasiadas veces, se "
                        + "reintenta en la próxima vuelta");
            };
            LogFields.round(event, result).log();
        }
    }
}
