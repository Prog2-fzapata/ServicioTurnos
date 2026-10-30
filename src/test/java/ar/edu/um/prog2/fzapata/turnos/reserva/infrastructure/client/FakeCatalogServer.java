package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// Servidor HTTP real y local que hace de catálogo: así los timeouts y las conexiones cortadas se prueban de verdad.
// Cada pedido consume la siguiente respuesta del guion; cuando el guion se acaba, repite la última
final class FakeCatalogServer implements AutoCloseable {

    interface Behavior {
        void handle(HttpExchange exchange) throws IOException, InterruptedException;
    }

    private final HttpServer server;
    private final Queue<Behavior> script = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private volatile Behavior last = exchange -> respond(exchange, 500, "");

    FakeCatalogServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", status == 200 ? "application/json"
                : "application/problem+json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    static Behavior status(int status, String body) {
        return exchange -> respond(exchange, status, body);
    }

    // Cierra la conexión sin responder: el cliente ve un corte
    static Behavior cutConnection() {
        return HttpExchange::close;
    }

    static Behavior slow(long millis) {
        return exchange -> {
            Thread.sleep(millis);
            respond(exchange, 200, "{}");
        };
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
        paths.add(exchange.getRequestURI().getPath());
        Behavior behavior = script.poll();
        if (behavior == null) {
            behavior = last;
        } else {
            last = behavior;
        }
        try {
            behavior.handle(exchange);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // El cliente ya cortó (por ejemplo, por timeout): no hay nada más que contestar
            exchange.close();
        }
    }

    FakeCatalogServer thenRespond(Behavior... behaviors) {
        script.addAll(List.of(behaviors));
        return this;
    }

    String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    int requests() {
        return requests.get();
    }

    List<String> authorizations() {
        return authorizations;
    }

    List<String> paths() {
        return paths;
    }

    @Override
    public void close() {
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }
}
