package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.service.ReservaService;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.AvailabilityResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper.AvailabilityDtoMapper;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper.AvailabilityQueryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final ReservaService reservaService;
    private final AvailabilityDtoMapper availabilityDtoMapper;
    private final AvailabilityQueryMapper availabilityQueryMapper;

    @Operation(summary = "Horarios libres de un profesional entre dos fechas: su agenda menos lo que la cátedra "
            + "informa como ocupado, sin horarios pasados. Es una foto: el veredicto real lo da el hold al reservar")
    @GetMapping
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @Parameter(description = "Id del profesional") @RequestParam Long professionalId,
            @Parameter(description = "Primer día, inclusivo (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último día, inclusivo (yyyy-MM-dd); no anterior a from")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(availabilityDtoMapper.toResponse(
                reservaService.getAvailability(availabilityQueryMapper.toQuery(professionalId, from, to))));
    }
}
