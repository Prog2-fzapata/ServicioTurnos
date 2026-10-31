package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Respuesta de la cátedra a la confirmación inicial: queda esperando el teléfono hasta expiresAt
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldConfirmation {

    private String reservationProcessId;
    private Instant expiresAt;
}
