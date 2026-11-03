package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.ReservationResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.StartReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper.ReservationDtoMapper;
import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtClaims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservaService reservaService;
    private final ReservationDtoMapper reservationDtoMapper;

    @Operation(summary = "Inicia una reserva: valida con catálogo, pide el bloqueo del horario a la cátedra y lo "
            + "confirma. Devuelve la reserva esperando el teléfono (WAITING_PHONE). El usuario sale del JWT")
    @PostMapping
    public ResponseEntity<ReservationResponse> startReservation(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartReservationRequest request) {
        String userId = jwt.getClaimAsString(JwtClaims.USER_ID);
        return new ResponseEntity<>(reservationDtoMapper.toResponse(
                reservaService.startReservation(reservationDtoMapper.toRequest(request, userId))), HttpStatus.CREATED);
    }
}
