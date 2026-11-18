package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraAuthenticationException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.ProblemResponse;
import lombok.extern.slf4j.Slf4j;

// Traduce las fallas del cliente HTTP a las excepciones de la aplicación. Los mensajes y el log llevan solo el status y
// el code: nunca el cuerpo, el JWT ni datos del paciente
@Slf4j
final class CatedraErrorTranslator {

    // Un code legítimo es corto y en mayúsculas; cualquier otra cosa no se copia a un mensaje ni a un log
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z_]{1,60}");

    // Matriz de errores del §13 del contrato: se decide por status y code, nunca por el texto del detalle.
    // Todo lo que no figure acá es un desajuste (CatedraResponseException)
    private static final Map<String, CatedraRejection> REJECTIONS = Map.ofEntries(
            Map.entry("409:SLOT_ALREADY_HELD", CatedraRejection.SLOT_ALREADY_HELD),
            Map.entry("409:SLOT_ALREADY_RESERVED", CatedraRejection.SLOT_ALREADY_RESERVED),
            Map.entry("400:PROFESSIONAL_DISABLED", CatedraRejection.PROFESSIONAL_DISABLED),
            Map.entry("404:PROFESSIONAL_NOT_FOUND", CatedraRejection.PROFESSIONAL_NOT_FOUND),
            Map.entry("400:INVALID_SLOT", CatedraRejection.INVALID_SLOT),
            Map.entry("404:HOLD_NOT_FOUND", CatedraRejection.HOLD_NOT_FOUND),
            Map.entry("404:HOLD_EXPIRED", CatedraRejection.HOLD_EXPIRED),
            Map.entry("409:RESERVATION_PROCESS_ALREADY_CONFIRMED",
                    CatedraRejection.RESERVATION_PROCESS_ALREADY_CONFIRMED),
            Map.entry("404:RESERVATION_NOT_FOUND", CatedraRejection.RESERVATION_NOT_FOUND),
            Map.entry("409:RESERVATION_NOT_CANCELLABLE", CatedraRejection.RESERVATION_NOT_CANCELLABLE));

    private CatedraErrorTranslator() {
    }

    static RuntimeException translate(RestClientException e) {
        return switch (e) {
            case RestClientResponseException response -> translateResponse(response);
            case ResourceAccessException access -> translateAccess(access);
            // Llegó una respuesta, pero su cuerpo no se pudo leer como JSON del contrato
            default -> new CatedraResponseException("La respuesta de la cátedra no se pudo leer", e);
        };
    }

    // ConnectException (rechazada) o HttpConnectTimeoutException: no se llegó a enviar nada
    static boolean neverConnected(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    // Sin la excepción original como causa: su mensaje trae el cuerpo y llegaría al log con la traza de un error
    // inesperado
    private static RuntimeException translateResponse(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (e.getStatusCode().is5xxServerError()) {
            log.atWarn().setMessage("La cátedra respondió con un error del servidor").addKeyValue("status", status).log();
            return new CatedraUnavailableException("La cátedra no está disponible (" + status + ")", null);
        }
        if (status == 401 || status == 403) {
            log.atError().setMessage("La cátedra rechazó el JWT técnico, revisar CATEDRA_JWT")
                    .addKeyValue("status", status).log();
            return new CatedraAuthenticationException(
                    "La cátedra rechazó el JWT técnico (" + status + "): revisar CATEDRA_JWT", null);
        }
        String code = codeOf(e);
        CatedraRejection rejection = REJECTIONS.get(status + ":" + code);
        if (rejection != null) {
            return new CatedraRejectedException(rejection);
        }
        log.atError().setMessage("La cátedra respondió con un error que turnos no esperaba")
                .addKeyValue("status", status).addKeyValue("code", code).log();
        return new CatedraResponseException("La cátedra respondió " + status + (code == null ? "" : " " + code), null);
    }

    // El cuerpo de un error puede no ser JSON o no traer code
    private static String codeOf(RestClientResponseException e) {
        try {
            ProblemResponse problem = e.getResponseBodyAs(ProblemResponse.class);
            String code = problem == null ? null : problem.getCode();
            return code != null && SAFE_CODE.matcher(code).matches() ? code : null;
        } catch (RestClientException | IllegalStateException unreadable) {
            return null;
        }
    }

    private static RuntimeException translateAccess(ResourceAccessException e) {
        if (neverConnected(e)) {
            log.atWarn().setMessage("No se pudo conectar con la cátedra").log();
            return new CatedraNotReachedException("No se pudo conectar con la cátedra", e);
        }
        // Corte o timeout después de enviar: el pedido pudo haber llegado
        log.atWarn().setMessage("La cátedra no respondió").addKeyValue("exception", e.getClass().getSimpleName()).log();
        return new CatedraUnavailableException("La cátedra no respondió", e);
    }
}
