package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// APPLIED: el estado cambió. ALREADY_IN_STATE: el proceso ya estaba en el destino, sin cambios (hace idempotente
// repetir un pedido). REJECTED: el diagrama no permite ese paso desde el estado actual, se ignora
public enum TransitionOutcome {
    APPLIED,
    ALREADY_IN_STATE,
    REJECTED
}
