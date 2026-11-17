package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;

public interface SubmitPhoneUseCase {

    // Vacío si la reserva no existe o es de otro usuario. Pasa la reserva a PHONE_QUEUED con el teléfono normalizado y
    // guarda el mensaje en el outbox, en una sola transacción. Lanza InvalidReservationStateException si no espera el
    // teléfono o la cátedra todavía no lo pidió, PhoneWindowExpiredException si pasó su vencimiento, y
    // ReservationConflictException si la versión chocó (un solo intento: quien llama lo repite)
    Optional<ReservationProcess> submitPhone(Long id, String userId, String phoneNumber);
}
