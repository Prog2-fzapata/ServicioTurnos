package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;

// Avisa que una reserva cambió. Es solo un aviso, no la fuente de verdad: quien lo implementa decide cómo entregarlo y
// no debe adelantarse al COMMIT ni propagar un fallo propio. Recibe el proceso tal como quedó guardado
public interface ReservationChangePublisher {

    void publish(ReservationProcess process);
}
