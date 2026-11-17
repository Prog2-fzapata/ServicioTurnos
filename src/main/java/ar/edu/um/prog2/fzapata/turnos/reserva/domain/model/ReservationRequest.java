package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

// Lo que pide el usuario para iniciar una reserva. userId sale siempre del JWT, nunca del cuerpo del pedido
public record ReservationRequest(String userId, Long professionalId, LocalDate date, LocalTime startTime) {
}
