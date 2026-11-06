package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneSubmissionRules;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;

@Component
public class ReservationUpdateMapper {

    public ReservationUpdate toUpdate(ReservationProcess process) {
        if (process == null) return null;
        return new ReservationUpdate(process.getId(), process.getStatus(),
                // Igual que en Reservation: el vencimiento solo tiene sentido mientras el proceso puede vencer
                process.getStatus().isExpirable() ? process.getExpiresAt() : null,
                PhoneSubmissionRules.canSubmit(process), process.getUpdatedAt());
    }

    // Id del proceso y su versión: crece con cada cambio, así la app distingue un aviso viejo de uno nuevo
    public String toEventId(ReservationProcess process) {
        return process.getId() + "-" + process.getVersion();
    }
}
