package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// El hold venció (o desapareció) antes de poder confirmarlo y no se consiguió otro dentro de los intentos permitidos
public class ReservationExpiredException extends RuntimeException {

    public ReservationExpiredException() {
        super("El bloqueo del horario venció antes de confirmarse");
    }
}
