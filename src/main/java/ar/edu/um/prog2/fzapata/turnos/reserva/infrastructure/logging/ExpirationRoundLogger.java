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

    // status es el estado en que estaba la reserva cuando se decidió
    private static void logged(LoggingEventBuilder event, ExpirationResult result) {
        event.addKeyValue("reservationId", result.processId()).addKeyValue("status", result.from().name()).log();
    }

    public void log(List<ExpirationResult> results) {
        for (ExpirationResult result : results) {
            switch (result.outcome()) {
                case EXPIRED -> logged(log.atInfo().setMessage("Vencimiento: reserva expirada"), result);
                case CONFIRMED_BY_CATEDRA -> logged(log.atWarn().setMessage("Vencimiento: la cátedra la tenía "
                        + "confirmada, se repara a CONFIRMED en vez de expirarla"), result);
                case UNCHANGED -> logged(log.atInfo().setMessage("Vencimiento: otra operación ya había cambiado la "
                        + "reserva"), result);
                case UNVERIFIED -> logged(log.atWarn().setMessage("Vencimiento: no se pudo comprobar con la cátedra, "
                        + "no se expira y se reintenta en la próxima vuelta"), result);
                case CONFLICT -> logged(log.atWarn().setMessage("Vencimiento: la versión chocó demasiadas veces, se "
                        + "reintenta en la próxima vuelta"), result);
            }
        }
    }
}
