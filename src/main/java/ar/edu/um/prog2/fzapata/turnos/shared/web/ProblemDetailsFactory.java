package ar.edu.um.prog2.fzapata.turnos.shared.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailsFactory {
    // Crea el objeto Problem Detail
    public ProblemDetail of(HttpStatus status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("path", path);
        problem.setProperty("code", code);
        return problem;
    }
    // Envuelve en una respuesta HTTP con el ContentType
    public ResponseEntity<ProblemDetail> response(HttpStatus status, String code, String detail, String path) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(of(status, code, detail, path));
    }

    // Respuestas que arma Spring por su cuenta (404, 405, 415...): se les agrega code y path
    // y se traduce el detalle al español
    public void complete(ProblemDetail problem, HttpStatus status, String path) {
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey("code")) {
            Standard standard = standardFor(status);
            problem.setDetail(standard.detail());
            problem.setProperty("code", standard.code());
        }
        if (properties == null || !properties.containsKey("path")) {
            problem.setProperty("path", path);
        }
    }

    // El detalle es genérico a propósito: nunca se exponen mensajes internos al cliente
    public ResponseEntity<ProblemDetail> internalError(String path) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor", path);
    }

    public ResponseEntity<ProblemDetail> unauthorized(String path) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Falta el token de autenticación o no es válido", path);
    }

    public ResponseEntity<ProblemDetail> forbidden(String path) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tiene permiso para realizar esta operación", path);
    }

    private Standard standardFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> new Standard("BAD_REQUEST", "La solicitud no es válida");
            case NOT_FOUND -> new Standard("NOT_FOUND", "El recurso solicitado no existe");
            case METHOD_NOT_ALLOWED -> new Standard("METHOD_NOT_ALLOWED",
                    "El método HTTP no está permitido para este recurso");
            case NOT_ACCEPTABLE -> new Standard("NOT_ACCEPTABLE", "El formato de respuesta pedido no está disponible");
            case UNSUPPORTED_MEDIA_TYPE -> new Standard("UNSUPPORTED_MEDIA_TYPE",
                    "El tipo de contenido enviado no está soportado");
            default -> status.is5xxServerError()
                    ? new Standard("INTERNAL_ERROR", "Error interno del servidor")
                    : new Standard("REQUEST_ERROR", "No se pudo procesar la solicitud");
        };
    }

    private record Standard(String code, String detail) {
    }
}
