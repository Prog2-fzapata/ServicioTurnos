package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.util.Optional;

// Qué pasó con un mensaje en una vuelta del relay. transition está presente si se publicó y se actualizó el proceso
public record PhoneDelivery(OutboxEvent event, boolean sent, Optional<TransitionResult> transition) {
}
