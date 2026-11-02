package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// De un error de la cátedra solo interesa el code: se decide por status y code, nunca por el texto del detalle
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProblemResponse {
    private String code;
}
