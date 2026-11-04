package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import ar.edu.um.prog2.fzapata.turnos.shared.config.StreamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Las conexiones SSE abiertas, por usuario. Vive en memoria: con una sola instancia alcanza (D12) y, si se pierde, la
// app reconecta y reconsulta. Cada lista se reemplaza entera al cambiar, así quien la lee ve siempre una copia estable.
// Es un SmartLifecycle para cerrar todas las conexiones al empezar el apagado: Spring espera a que terminen los pedidos en curso y un
// stream abierto no termina solo, así que sin esto cada reinicio se demoraría los 30 s del plazo de apagado
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationStreamRegistry implements SmartLifecycle {

    static final String EVENT_NAME = "reservation-updated";
    private static final String PING = "ping";

    private final StreamProperties properties;
    private final Clock clock;
    private final Map<String, List<Connection>> connections = new ConcurrentHashMap<>();
    private volatile boolean running;

    // tokenExpiresAt es null si el JWT no trae exp: esa conexión no se cierra por vencimiento
    private record Connection(SseEmitter emitter, Instant tokenExpiresAt) {
    }

    private void remove(String userId, Connection connection) {
        connections.computeIfPresent(userId, (key, current) -> {
            List<Connection> updated = new ArrayList<>(current);
            updated.remove(connection);
            return updated.isEmpty() ? null : updated;
        });
    }

    private void complete(Connection connection) {
        try {
            connection.emitter().complete();
        } catch (RuntimeException e) {
            log.debug("La conexión ya estaba cerrada", e);
        }
    }

    // Una conexión que no responde se descarta: el contenido se perdió, pero la app reconsulta al reconectar
    private void deliver(String userId, Connection connection, SseEventBuilder event) {
        try {
            connection.emitter().send(event);
        } catch (IOException | RuntimeException e) {
            log.debug("Se descarta una conexión del stream que no responde", e);
            remove(userId, connection);
            complete(connection);
        }
    }

    // El primer envío manda las cabeceras: sin él el cliente esperaría hasta el primer evento o latido
    public SseEmitter register(String userId, Instant tokenExpiresAt) {
        SseEmitter emitter = new SseEmitter(0L);
        Connection connection = new Connection(emitter, tokenExpiresAt);
        emitter.onCompletion(() -> remove(userId, connection));
        emitter.onError(error -> remove(userId, connection));

        List<Connection> evicted = new ArrayList<>();
        connections.compute(userId, (key, current) -> {
            List<Connection> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
            updated.add(connection);
            while (updated.size() > properties.maxConnectionsPerUser()) {
                evicted.add(updated.removeFirst());
            }
            return updated;
        });
        evicted.forEach(this::complete);

        deliver(userId, connection, SseEmitter.event().comment(PING));
        return emitter;
    }

    public void send(String userId, String eventId, Object data) {
        for (Connection connection : connections.getOrDefault(userId, List.of())) {
            deliver(userId, connection,
                    SseEmitter.event().name(EVENT_NAME).id(eventId).data(data, MediaType.APPLICATION_JSON));
        }
    }

    // Manda el latido y cierra las conexiones cuyo JWT ya venció: ya no tiene sentido seguir avisándoles
    public void heartbeat() {
        Instant now = clock.instant();
        connections.forEach((userId, current) -> current.forEach(connection -> {
            if (connection.tokenExpiresAt() != null && !connection.tokenExpiresAt().isAfter(now)) {
                remove(userId, connection);
                complete(connection);
            } else {
                deliver(userId, connection, SseEmitter.event().comment(PING));
            }
        }));
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        List<Connection> open = connections.values().stream().flatMap(List::stream).toList();
        connections.clear();
        open.forEach(this::complete);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    // El más alto posible: se detiene antes que el servidor web, que es quien espera a las conexiones abiertas
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    int connectionCount(String userId) {
        return connections.getOrDefault(userId, List.of()).size();
    }
}
