package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.EventResult;

public interface ProcessCatedraEventUseCase {

    // Aplica el evento y registra su eventId en una sola transacción: o quedan las dos cosas o ninguna. Un evento ya
    // procesado, de un proceso desconocido o de un tipo desconocido no cambia nada pero se registra. Un solo intento:
    // si dos escrituras chocan por la versión lanza ReservationConflictException, y quien lo llama lo repite
    EventResult processEvent(CatedraEvent event);
}
