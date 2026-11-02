package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Resultado de cancelar. Cancelar una reserva ya cancelada es idempotente y devuelve su estado actual
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cancellation {

    private Long reservationId;
    private String reservationProcessId;
    private Instant cancelledAt;
}
