package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Lleva el nombre del paciente: sin toString a propósito para que no llegue a un log
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmRequest {
    private String externalPatientId;
    private String patientFirstName;
    private String patientLastName;
}
