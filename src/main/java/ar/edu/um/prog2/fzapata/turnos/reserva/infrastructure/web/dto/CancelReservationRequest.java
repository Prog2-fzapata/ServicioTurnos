package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El cuerpo es opcional: sin motivo se cancela igual
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelReservationRequest {

    @Size(max = 500, message = "reason no puede tener más de 500 caracteres")
    private String reason;
}
