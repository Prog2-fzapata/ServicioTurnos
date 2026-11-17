package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka.dto;

import java.time.Instant;

// Forma del JSON de AdditionalInformationSubmitted (anexo §15.5). Todos los campos son obligatorios; eventType y
// schemaVersion llevan exactamente los valores del contrato
public record PhoneSubmittedMessage(String eventId, String eventType, String groupId, String reservationProcessId,
        String holdId, String requestEventId, Information information, Instant occurredAt, int schemaVersion) {

    public static final String EVENT_TYPE = "AdditionalInformationSubmitted";
    public static final int SCHEMA_VERSION = 1;

    public record Information(String phoneNumber) {
    }
}
