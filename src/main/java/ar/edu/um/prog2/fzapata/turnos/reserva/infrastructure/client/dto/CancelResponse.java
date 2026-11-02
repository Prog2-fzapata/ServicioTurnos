package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
@JsonIgnoreProperties(ignoreUnknown = true)
public class CancelResponse {
    private Long reservationId;
    private String reservationProcessId;
    private Instant cancelledAt;
}
