package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

public class ProfessionalNotFoundException extends RuntimeException {

    public ProfessionalNotFoundException(Long professionalId) {
        super("No existe el profesional " + professionalId);
    }
}
