package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// APPLIED: el estado cambió. DATA_COMPLETED: el proceso ya estaba en el destino pero faltaban datos y se completaron
// (por ejemplo, el requestEventId que llega con el pedido de teléfono). ALREADY_IN_STATE: el proceso ya estaba en el
// destino y no hay nada que completar (hace idempotente repetir un pedido). REJECTED: el diagrama no permite ese paso
// desde el estado actual, se ignora
public enum TransitionOutcome {
    APPLIED,
    DATA_COMPLETED,
    ALREADY_IN_STATE,
    REJECTED
}
