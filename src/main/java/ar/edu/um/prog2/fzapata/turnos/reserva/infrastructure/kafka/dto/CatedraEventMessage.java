package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.kafka.dto;

import java.time.Instant;

// Forma común del JSON de los eventos de reserva (anexo §15.4 a §15.10). Los campos que no se usan se ignoran, como
// pide el contrato; reason y expiresAt solo vienen en algunos tipos
public record CatedraEventMessage(String eventId, String eventType, String reservationProcessId, Long reservationId,
        String reason, Instant expiresAt) {
}
