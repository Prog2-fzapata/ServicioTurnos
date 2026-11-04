package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.util.List;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ExpirationResult;

public interface ExpireOverdueReservationsUseCase {

    // Una tanda: expira los procesos vencidos hace más que el margen, del más viejo al más nuevo. Antes de expirar uno
    // al que el teléfono pudo haberle llegado a la cátedra, le pregunta si ya lo tiene confirmado. Un fallo en un
    // proceso no frena a los demás
    List<ExpirationResult> expireOverdue();
}
