package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;

public interface StartReservationUseCase {

    // Devuelve el proceso ya en WAITING_PHONE. Si el profesional no existe, si él o su categoría están deshabilitados,
    // el horario es inválido, faltan nombre o apellido, el horario está ocupado o no se consigue un hold confirmado,
    // lanza la excepción de negocio correspondiente
    ReservationProcess startReservation(ReservationRequest request);
}
