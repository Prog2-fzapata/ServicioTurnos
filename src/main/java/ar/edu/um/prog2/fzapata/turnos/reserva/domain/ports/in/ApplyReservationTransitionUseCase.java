package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationTransition;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.TransitionResult;

public interface ApplyReservationTransitionUseCase {

    // Vacío si el proceso no existe. Un paso que el diagrama no permite no es una excepción: vuelve como REJECTED.
    // Si dos actualizaciones chocan por la versión se relee el proceso y se reintenta; si se agotan los intentos lanza
    // ReservationConflictException. No es válido llamarlo dentro de una transacción ya abierta: el reintento necesita
    // una transacción nueva por intento
    Optional<TransitionResult> applyTransition(Long processId, ReservationTransition transition);

    // Un solo intento, sin releer ni repetir: es el que corresponde dentro de una transacción ya abierta (el
    // consumidor de eventos), donde repetir no sirve. Si la versión cambió lanza ReservationConflictException y
    // quien tiene la transacción decide qué hacer. Mismo resultado que applyTransition
    Optional<TransitionResult> applyTransitionOnce(Long processId, ReservationTransition transition);
}
