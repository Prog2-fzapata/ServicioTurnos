package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.util.Arrays;
import java.util.Optional;

// Los eventos de reserva que la cátedra publica en el topic de acciones (anexo §15). name es el valor de eventType
// en el mensaje. Un tipo que no figura acá se ignora: el contrato pide tolerar lo que no se conoce
public enum CatedraEventType {
    ADDITIONAL_INFORMATION_REQUESTED("AdditionalInformationRequested"),
    ADDITIONAL_INFORMATION_REJECTED("AdditionalInformationRejected"),
    APPOINTMENT_CONFIRMED("AppointmentConfirmed"),
    APPOINTMENT_PROCESS_EXPIRED("AppointmentProcessExpired"),
    APPOINTMENT_PROCESS_INVALID("AppointmentProcessInvalid"),
    APPOINTMENT_CANCELLED("AppointmentCancelled");

    private final String wireName;

    CatedraEventType(String wireName) {
        this.wireName = wireName;
    }

    public static Optional<CatedraEventType> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(type -> type.wireName.equals(wireName)).findFirst();
    }
}
