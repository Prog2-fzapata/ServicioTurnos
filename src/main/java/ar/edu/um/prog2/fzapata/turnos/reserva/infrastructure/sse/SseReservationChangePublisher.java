package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationChangePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseReservationChangePublisher implements ReservationChangePublisher {

    private final ReservationStreamRegistry registry;
    private final ReservationUpdateMapper mapper;

    // El contenido se arma ahora, con el proceso tal como quedó: después del COMMIT ya no se lee de ningún lado
    @Override
    public void publish(ReservationProcess process) {
        String userId = process.getUserId();
        String eventId = mapper.toEventId(process);
        ReservationUpdate update = mapper.toUpdate(process);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Dentro de una transacción el cambio todavía no se ve desde afuera: si se avisara ahora la app podría
            // consultar y no encontrarlo, o enterarse de algo que después se deshace
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(userId, eventId, update);
                }
            });
        } else {
            send(userId, eventId, update);
        }
    }

    // Un fallo del aviso no debe deshacer ni hacer reintentar el trabajo que ya se guardó
    private void send(String userId, String eventId, ReservationUpdate update) {
        try {
            registry.send(userId, eventId, update);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar el cambio {} por el stream", eventId, e);
        }
    }
}
