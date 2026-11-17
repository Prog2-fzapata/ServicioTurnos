package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Qué pasó con un proceso dudoso al arrancar. ORPHAN_FAILED: HOLD_REQUESTED sin ids que pasó a FAILED.
// CONFIRMED_BY_CATEDRA: la cátedra lo tenía confirmado y turnos no se había enterado, así que se repara a CONFIRMED.
// STILL_OPEN: la cátedra no lo tiene confirmado y se deja como está. UNCHANGED: otro lo cambió antes y el paso ya no
// corresponde. UNVERIFIED: no se pudo comprobar con la cátedra y por eso no se tocó. CONFLICT: la versión chocó más
// veces de las permitidas
public enum RecoveryOutcome {
    ORPHAN_FAILED,
    CONFIRMED_BY_CATEDRA,
    STILL_OPEN,
    UNCHANGED,
    UNVERIFIED,
    CONFLICT
}
