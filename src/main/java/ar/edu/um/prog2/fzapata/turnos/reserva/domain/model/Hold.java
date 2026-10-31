package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Bloqueo temporal de un horario. La cátedra no permite recuperar estos ids si se pierde la respuesta
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Hold {

    private String holdId;
    private String reservationProcessId;
    private Instant expiresAt;
}
