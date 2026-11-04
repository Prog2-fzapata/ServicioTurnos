package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.in;

import java.time.Instant;
import java.util.List;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.RecoveryResult;

public interface RecoverReservationsUseCase {

    // Una tanda al arrancar. Pasa a FAILED los HOLD_REQUESTED sin ids creados antes de instanceStartedAt (los creados
    // después son pedidos en curso de esta instancia) y reconcilia con la cátedra los PHONE_QUEUED y PHONE_SUBMITTED:
    // los que ella tiene confirmados se reparan a CONFIRMED; ante la duda no se toca nada. Un fallo en un proceso no
    // frena a los demás
    List<RecoveryResult> recover(Instant instanceStartedAt);
}
