package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Lo que una ronda de la aplicación decide para un proceso: cuál es y en qué estado estaba cuando se decidió. Solo ids
// y estados: nada del paciente
public interface RoundResult {

    Long processId();

    ReservationStatus from();
}
