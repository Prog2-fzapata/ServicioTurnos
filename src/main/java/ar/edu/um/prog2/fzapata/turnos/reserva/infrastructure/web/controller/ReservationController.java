package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.CancelReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.ReservationResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.StartReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.SubmitPhoneRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper.ReservationDtoMapper;
import ar.edu.um.prog2.fzapata.turnos.shared.security.JwtClaims;
import ar.edu.um.prog2.fzapata.turnos.shared.web.PaginationHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// El usuario sale siempre del JWT: una reserva ajena responde como si no existiera
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

    @Operation(summary = "Lista las reservas del usuario, de la más reciente a la más antigua por fecha y hora del "
            + "turno (el orden es fijo). Sin status no incluye las EXPIRED ni las FAILED; con status devuelve "
            + "exactamente las de ese estado. El total va en X-Total-Count")
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listReservations(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Solo las reservas en este estado") @RequestParam(required = false)
            ReservationStatus status,
            @ParameterObject Pageable pageable) {
        PagedResult<ReservationProcess> result = reservaService.listReservations(
                jwt.getClaimAsString(JwtClaims.USER_ID), status, reservationDtoMapper.toPageQuery(pageable));
        return ResponseEntity.ok()
                .headers(PaginationHeaders.create(ServletUriComponentsBuilder.fromCurrentRequest(),
                        result.pageQuery().page(), result.pageQuery().size(), result.totalElements()))
                .body(result.content().stream().map(reservationDtoMapper::toResponse).toList());
    }

    @Operation(summary = "Devuelve una reserva del usuario. Una ajena responde 404, igual que una inexistente")
    @GetMapping("/{id}")
    public ReservationResponse getReservation(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return reservationDtoMapper
                .toResponse(reservaService.getReservation(id, jwt.getClaimAsString(JwtClaims.USER_ID)));
    }

    @Operation(summary = "Cancela una reserva confirmada del usuario. El motivo es opcional. Cancelar una ya "
            + "cancelada devuelve su estado actual")
    @PostMapping("/{id}/cancel")
    public ReservationResponse cancelReservation(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id, @Valid @RequestBody(required = false) CancelReservationRequest request) {
        return reservationDtoMapper.toResponse(reservaService.cancelReservation(id,
                jwt.getClaimAsString(JwtClaims.USER_ID), reservationDtoMapper.toReason(request)));
    }

    @Operation(summary = "Envía el teléfono del paciente. Solo con la reserva en WAITING_PHONE y el pedido de la "
            + "cátedra ya recibido (canSubmitPhone). La deja en PHONE_QUEUED y responde 202: el resultado final "
            + "llega después, cuando la cátedra confirma o rechaza")
    @PostMapping("/{id}/phone")
    public ResponseEntity<ReservationResponse> submitPhone(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id, @Valid @RequestBody SubmitPhoneRequest request) {
        return new ResponseEntity<>(reservationDtoMapper.toResponse(reservaService.submitPhone(id,
                jwt.getClaimAsString(JwtClaims.USER_ID), request.getPhoneNumber())), HttpStatus.ACCEPTED);
    }
}
