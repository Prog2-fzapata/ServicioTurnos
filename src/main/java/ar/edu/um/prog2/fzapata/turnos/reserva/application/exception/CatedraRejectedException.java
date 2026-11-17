package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;

// La cátedra rechazó la operación por una regla propia (horario ocupado, hold vencido...): no es una falla
public class CatedraRejectedException extends RuntimeException {

    private final CatedraRejection rejection;

    public CatedraRejectedException(CatedraRejection rejection) {
        super("La cátedra rechazó la operación: " + rejection);
        this.rejection = rejection;
    }

    public CatedraRejection getRejection() {
        return rejection;
    }
}
