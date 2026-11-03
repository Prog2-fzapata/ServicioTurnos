package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ProcessedEvent;

public interface ProcessedEventRepository {

    boolean existsById(String eventId);

    void save(ProcessedEvent event);
}
