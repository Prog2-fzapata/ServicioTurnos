package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse.ReservationStreamRegistry;
import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtClaims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

// Administrar conexiones abiertas es transporte y no lógica de negocio, así que inyecta el registro y no el servicio
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationStreamController {

    private final ReservationStreamRegistry registry;

    @Operation(summary = "Abre un stream de eventos (SSE) con los cambios de las reservas del usuario: un "
            + "reservation-updated por cambio y un comentario ': ping' de latido. No es la fuente de verdad: al "
            + "conectar o reconectar la app consulta GET /api/reservations. Cierra el stream cuando vence el JWT")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return registry.register(jwt.getClaimAsString(JwtClaims.USER_ID), jwt.getExpiresAt());
    }
}
