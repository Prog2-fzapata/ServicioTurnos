package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;

// Los datos del mensaje AdditionalInformationSubmitted (anexo §15.5). requestEventId es el eventId del pedido de la
// cátedra al que se responde
public record PhoneSubmission(String eventId, String reservationProcessId, String holdId, String requestEventId,
        String phoneNumber, Instant occurredAt) {
}
