package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.util.List;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneDelivery;

public interface PublishPendingPhonesUseCase {

    // Una vuelta del relay: publica los mensajes pendientes, del más viejo al más nuevo. Uno que no se puede publicar
    // queda pendiente con un intento más, sin cortar la vuelta. Repetir un mensaje es seguro: lleva siempre el mismo
    // eventId y la cátedra descarta el duplicado
    List<PhoneDelivery> publishPending();
}
