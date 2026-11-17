package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import java.util.List;
import java.util.Optional;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;

public interface OutboxRepository {

    // Crea (sin id) o actualiza
    OutboxEvent save(OutboxEvent event);

    Optional<OutboxEvent> findById(Long id);

    // Los PENDING, del más viejo al más nuevo
    List<OutboxEvent> findPending(int limit);
}
