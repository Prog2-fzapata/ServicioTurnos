package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto;

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
public class CancelRequest {
    private String reason;
}
