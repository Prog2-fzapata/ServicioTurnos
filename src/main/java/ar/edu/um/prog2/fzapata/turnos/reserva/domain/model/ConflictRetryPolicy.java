package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Intentos en total, el primero incluido, cuando dos actualizaciones del mismo proceso chocan por la versión
public record ConflictRetryPolicy(int maxAttempts) {
}
