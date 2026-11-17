package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PhoneSubmission;

// El formato del mensaje es un detalle del contrato con la cátedra, no de la aplicación
public interface PhoneMessageSerializer {

    String toPayload(PhoneSubmission submission);
}
