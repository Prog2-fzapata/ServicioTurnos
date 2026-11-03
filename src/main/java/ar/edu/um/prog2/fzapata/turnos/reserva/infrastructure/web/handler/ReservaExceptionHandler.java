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
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidReservationStateException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidSlotException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PhoneWindowExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.PatientNameRequiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationExpiredException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationNotCancellableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationProcessNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.SlotUnavailableException;
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

    // La app pide los datos faltantes y reintenta
    @ExceptionHandler(PatientNameRequiredException.class)
    public ResponseEntity<ProblemDetail> handlePatientNameRequired(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.BAD_REQUEST, "PATIENT_NAME_REQUIRED",
                "La cuenta necesita nombre y apellido de al menos 2 caracteres para reservar", request.getRequestURI());
    }

    @ExceptionHandler(InvalidSlotException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSlot(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.BAD_REQUEST, "INVALID_SLOT",
                "El horario no está en la agenda del profesional o ya pasó", request.getRequestURI());
    }

    @ExceptionHandler(SlotUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleSlotUnavailable(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE",
                "El horario ya no está disponible", request.getRequestURI());
    }

    @ExceptionHandler(ReservationExpiredException.class)
    public ResponseEntity<ProblemDetail> handleReservationExpired(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.CONFLICT, "RESERVATION_EXPIRED",
                "El bloqueo del horario venció antes de confirmarse, intente nuevamente", request.getRequestURI());
    }

    // También cuando la reserva es de otro usuario: no se revela que existe
    @ExceptionHandler(ReservationProcessNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleReservationNotFound(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
                "La reserva no existe", request.getRequestURI());
    }

    @ExceptionHandler(ReservationNotCancellableException.class)
    public ResponseEntity<ProblemDetail> handleReservationNotCancellable(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELLABLE",
                "La reserva no se puede cancelar en su estado actual", request.getRequestURI());
    }

    // Incluye que la cátedra todavía no pidió el teléfono: la app espera a canSubmitPhone
    @ExceptionHandler(InvalidReservationStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidReservationState(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE",
                "La reserva no puede recibir el teléfono en su estado actual", request.getRequestURI());
    }

    @ExceptionHandler(PhoneWindowExpiredException.class)
    public ResponseEntity<ProblemDetail> handlePhoneWindowExpired(HttpServletRequest request) {
        return problemDetailsFactory.response(HttpStatus.CONFLICT, "RESERVATION_EXPIRED",
                "Venció el tiempo para enviar el teléfono, inicie una nueva reserva", request.getRequestURI());
    }
}
