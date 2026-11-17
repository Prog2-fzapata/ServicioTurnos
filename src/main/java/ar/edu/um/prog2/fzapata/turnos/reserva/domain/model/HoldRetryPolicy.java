package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Intentos en total, el primero incluido, para conseguir un hold confirmado: cada intento es un hold más su confirmación
public record HoldRetryPolicy(int maxAttempts) {
}
