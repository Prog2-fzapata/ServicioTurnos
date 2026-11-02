package ar.edu.um.prog2.fzapata.turnos.reserva.application.exception;

// No se pudo establecer la conexión con la cátedra: el pedido nunca se envió, así que se sabe que no tuvo efecto
// y repetirlo es seguro. Es un caso particular de "no disponible"
public class CatedraNotReachedException extends CatedraUnavailableException {

    public CatedraNotReachedException(String message, Throwable cause) {
        super(message, cause);
    }
}
