package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;

public interface StartReservationUseCase {

    // Vacío si el profesional no existe. Devuelve el proceso ya en WAITING_PHONE. Si el profesional o su categoría están
    // deshabilitados, el horario es inválido, faltan nombre o apellido, el horario está ocupado o no se consigue un hold
    // confirmado, lanza la excepción de negocio correspondiente
    Optional<ReservationProcess> startReservation(ReservationRequest request);
}
