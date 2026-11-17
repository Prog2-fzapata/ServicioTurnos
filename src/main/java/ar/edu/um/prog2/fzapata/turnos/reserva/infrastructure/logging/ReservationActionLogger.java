package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.logging;

import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import lombok.extern.slf4j.Slf4j;

// Lo que hizo el usuario por HTTP. Los casos de uso no loguean (A19): lo hace quien los dispara, el controller, con la
// reserva tal como quedó. Solo el id, el usuario (id interno) y el estado: nunca el teléfono, el nombre ni el motivo
@Slf4j
@Component
public class ReservationActionLogger {

    private static void logged(LoggingEventBuilder event, ReservationProcess process) {
        LogFields.reservation(event, process.getId(), process.getUserId())
                .addKeyValue("status", process.getStatus().name()).log();
    }

    public void started(ReservationProcess process) {
        logged(log.atInfo().setMessage("Reserva iniciada"), process);
    }

    public void phoneReceived(ReservationProcess process) {
        logged(log.atInfo().setMessage("Teléfono recibido"), process);
    }

    public void cancelled(ReservationProcess process) {
        logged(log.atInfo().setMessage("Reserva cancelada"), process);
    }
}
