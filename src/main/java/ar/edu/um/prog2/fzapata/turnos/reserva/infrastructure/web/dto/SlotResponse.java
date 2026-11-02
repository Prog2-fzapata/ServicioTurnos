package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;

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
public class SlotResponse {
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
}
