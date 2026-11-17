package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityResponse {
    private Long professionalId;
    private LocalDate from;
    private LocalDate to;
    private List<SlotResponse> slots;
}
