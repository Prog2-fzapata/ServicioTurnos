package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto;

import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.validation.ValidPhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class SubmitPhoneRequest {

    // El tope solo acota la entrada: la regla real (7 a 15 dígitos) se aplica después de quitar los separadores
    @NotBlank(message = "phoneNumber es obligatorio")
    @Size(max = 40, message = "phoneNumber no puede tener más de 40 caracteres")
    @ValidPhoneNumber(message = "phoneNumber debe tener entre 7 y 15 dígitos, con un + opcional al principio")
    private String phoneNumber;
}
