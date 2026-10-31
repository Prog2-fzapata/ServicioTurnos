package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// Servidor HTTP real y local que hace de otro servicio: así los timeouts y las conexiones cortadas se prueban de
// verdad. Cada pedido consume la siguiente respuesta del guion; cuando el guion se acaba, repite la última
final class FakeHttpServer implements AutoCloseable {

    interface Behavior {
        void handle(HttpExchange exchange) throws IOException, InterruptedException;
    }

    // Lo que recibió el servidor: sirve para comprobar qué envía el cliente
    record Request(String method, String rawPath, String rawQuery, String authorization, String contentType,
            String body) {
    }

    private final HttpServer server;
    private final Queue<Behavior> script = new ConcurrentLinkedQueue<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Behavior last = exchange -> respond(exchange, 500, "", Map.of());

    FakeHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    static void respond(HttpExchange exchange, int status, String body, Map<String, String> headers)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", status < 300 ? "application/json"
                : "application/problem+json");
        headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    static Behavior status(int status, String body) {
        return exchange -> respond(exchange, status, body, Map.of());
    }

    static Behavior status(int status, String body, Map<String, String> headers) {
        return exchange -> respond(exchange, status, body, headers);
    }

    // Cierra la conexión sin responder: el cliente ve un corte
    static Behavior cutConnection() {
        return HttpExchange::close;
    }

    static Behavior slow(long millis) {
        return exchange -> {
            Thread.sleep(millis);
            respond(exchange, 200, "{}", Map.of());
        };
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"), body));
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

    FakeHttpServer thenRespond(Behavior... behaviors) {
        script.addAll(List.of(behaviors));
        return this;
    }

    String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    int requestCount() {
        return requests.size();
    }

    List<Request> requests() {
        return requests;
    }

    Request firstRequest() {
        return requests.get(0);
    }

    List<String> authorizations() {
        return requests.stream().map(Request::authorization).toList();
    }

    List<String> paths() {
        return requests.stream().map(Request::rawPath).toList();
    }

    @Override
    public void close() {
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdownNow();
    }
}
