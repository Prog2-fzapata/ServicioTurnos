package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// A dónde y como quién se publica el teléfono: el topic de la cuenta y su groupId (campo del mensaje)
public record PhoneMessageSettings(String topic, String accountGroupId) {
}
