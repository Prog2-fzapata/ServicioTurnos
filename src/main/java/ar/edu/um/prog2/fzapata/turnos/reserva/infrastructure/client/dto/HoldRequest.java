package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// La hora va como HH:mm, igual que el ejemplo del contrato de la cátedra
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldRequest {
    private Long professionalId;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime startTime;
    private String externalPatientId;
}
