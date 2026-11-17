package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Duration;

// grace: cuánto tiempo después de su expiresAt se deja vivir un proceso antes de expirarlo, para que llegue un evento
// de la cátedra que viene en camino. batchSize: cuántos procesos toma la tarea en cada vuelta
public record ExpirationPolicy(Duration grace, int batchSize) {
}
