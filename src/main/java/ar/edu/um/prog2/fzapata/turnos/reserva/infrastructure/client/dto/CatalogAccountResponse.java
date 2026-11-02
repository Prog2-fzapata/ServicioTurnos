package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Respuesta de GET /api/account de catálogo: de ahí solo interesan el nombre y el apellido
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogAccountResponse {
    private String firstName;
    private String lastName;
}
