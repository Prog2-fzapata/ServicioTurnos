package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Respuesta de GET /api/professionals/{id} de catálogo
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogProfessionalResponse {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private List<CatalogScheduleResponse> weeklySchedules;
}
