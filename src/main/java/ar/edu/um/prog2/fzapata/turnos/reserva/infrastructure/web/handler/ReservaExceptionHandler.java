package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidDateRangeException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.shared.web.ProblemDetailsFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

// Se consulta antes que el handler compartido, que atrapa cualquier Exception
@RestControllerAdvice(basePackages = "ar.edu.um.prog2.fzapata.turnos.reserva")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ReservaExceptionHandler {

    private final ProblemDetailsFactory problemDetailsFactory;

    // Sin catálogo no se pueden iniciar operaciones que necesiten información vigente (enunciado, sección 8)
    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleCatalogUnavailable(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE",
                "El servicio de catálogo no está disponible, intente nuevamente en unos minutos",
                request.getRequestURI());
    }

    // Incluye CatedraNotReachedException. Qué hacer con un resultado desconocido lo decide cada caso de uso
    @ExceptionHandler(CatedraUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleCatedraUnavailable(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.SERVICE_UNAVAILABLE, "CATEDRA_UNAVAILABLE",
                "El servicio central de turnos no está disponible, intente nuevamente en unos minutos",
                request.getRequestURI());
    }

    @ExceptionHandler(ProfessionalNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleProfessionalNotFound(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.NOT_FOUND, "PROFESSIONAL_NOT_FOUND",
                "El profesional no existe", request.getRequestURI());
    }

    // También cuando solo está deshabilitada su categoría: el cliente no necesita distinguirlo
    @ExceptionHandler(ProfessionalDisabledException.class)
    public ResponseEntity<ProblemDetail> handleProfessionalDisabled(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.BAD_REQUEST, "PROFESSIONAL_DISABLED",
                "El profesional no está habilitado", request.getRequestURI());
    }

    // El mensaje lo escribe el propio servicio, sin datos del pedido: es seguro mostrarlo
    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ProblemDetail> handleInvalidDateRange(InvalidDateRangeException ex,
            HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", ex.getMessage(),
                request.getRequestURI());
    }
}
