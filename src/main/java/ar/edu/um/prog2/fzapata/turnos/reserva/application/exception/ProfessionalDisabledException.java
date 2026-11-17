package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// El profesional, o la categoría a la que pertenece, está deshabilitado: no se ofrecen horarios
public class ProfessionalDisabledException extends RuntimeException {

    public ProfessionalDisabledException(String message) {
        super(message);
    }
}
