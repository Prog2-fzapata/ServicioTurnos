package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatalogUnavailableException;
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
}
