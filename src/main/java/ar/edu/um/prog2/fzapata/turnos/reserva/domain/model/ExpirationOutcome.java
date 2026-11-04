package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Qué pasó con un proceso vencido en una vuelta de la tarea. EXPIRED: pasó a EXPIRED. CONFIRMED_BY_CATEDRA: la cátedra
// lo tenía confirmado y turnos no se había enterado, así que se repara a CONFIRMED. UNCHANGED: otro lo cambió antes y
// el paso ya no corresponde. UNVERIFIED: no se pudo comprobar con la cátedra y por eso no se tocó. CONFLICT: la versión
// chocó más veces de las permitidas. Los dos últimos se reintentan en la vuelta siguiente
public enum ExpirationOutcome {
    EXPIRED,
    CONFIRMED_BY_CATEDRA,
    UNCHANGED,
    UNVERIFIED,
    CONFLICT
}
