package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;

public interface CancelReservationUseCase {

    // Vacío si no existe o es de otro usuario. Cancela una reserva CONFIRMED (primero en la cátedra y recién entonces
    // en el proceso local); una ya CANCELLED se devuelve tal cual, sin llamar a nadie. Cualquier otro estado, o un
    // rechazo de la cátedra por no poder cancelarla, lanza ReservationNotCancellableException. reason es opcional
    Optional<ReservationProcess> cancelReservation(Long id, String userId, String reason);
}
