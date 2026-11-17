package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// La cuenta no tiene nombre o apellido (o tienen menos de 2 caracteres): la cátedra lo exige para reservar
public class PatientNameRequiredException extends RuntimeException {

    public PatientNameRequiredException() {
        super("La cuenta necesita nombre y apellido de al menos 2 caracteres para reservar");
    }
}
