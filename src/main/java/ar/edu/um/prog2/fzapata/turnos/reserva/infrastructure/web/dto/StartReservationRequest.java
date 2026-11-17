package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Sin el usuario: sale del JWT. Un campo userId en el cuerpo se ignora
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartReservationRequest {

    @NotNull(message = "professionalId es obligatorio")
    @Positive(message = "professionalId debe ser positivo")
    private Long professionalId;

    @NotNull(message = "date es obligatorio (yyyy-MM-dd)")
    private LocalDate date;

    @NotNull(message = "startTime es obligatorio (HH:mm)")
    private LocalTime startTime;
}
