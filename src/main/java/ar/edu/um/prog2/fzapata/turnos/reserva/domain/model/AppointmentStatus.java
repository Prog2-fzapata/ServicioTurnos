package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Estados de una reserva en la cátedra (no son los de la máquina de estados local de turnos)
public enum AppointmentStatus {
    PHONE_PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}
