package ar.edu.um.prog2.fzapata.turnos.shared.web;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Último en la fila: los handlers de cada slice se consultan primero y este atrapa lo que ninguno maneja
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ProblemDetailsFactory problemDetailsFactory;

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getObjectName(), error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = problemDetailsFactory.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Los datos enviados no son válidos", path(request));
        problem.setProperty("fieldErrors", violations);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problemDetailsFactory.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "El cuerpo de la solicitud no es un JSON válido", path(request));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // Pasan por acá todas las respuestas de la clase base, también las que no sobrescribimos (404, 405, 415...)
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (response != null && status != null && response.getBody() instanceof ProblemDetail problem) {
            problemDetailsFactory.complete(problem, status, path(request));
        }
        return response;
    }

    // La seguridad por método (@PreAuthorize) lanza la excepción dentro del controller: sin este handler
    // la atraparía el genérico de abajo y devolvería 500 en lugar de 403
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(HttpServletRequest request) {
        return problemDetailsFactory.forbidden(request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {}", request.getRequestURI(), ex);
        return problemDetailsFactory.internalError(request.getRequestURI());
    }

    private String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
    }
}
