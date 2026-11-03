package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;

public interface MarkPhoneSentUseCase {

    // Marca el mensaje como enviado y pasa el proceso a PHONE_SUBMITTED, en una sola transacción. Si el proceso ya
    // avanzó (la cátedra contestó antes del ack) ese paso se ignora y nada retrocede. Vacío si el mensaje ya estaba
    // enviado. Un solo intento: ReservationConflictException si la versión chocó
    Optional<TransitionResult> markSent(OutboxEvent event);
}
