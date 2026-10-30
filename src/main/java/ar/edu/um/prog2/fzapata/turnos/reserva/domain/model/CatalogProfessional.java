package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un profesional con su agenda vigente, tal como lo informa catálogo. Incluye lo deshabilitado:
// qué hacer con eso lo decide quien lo usa
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProfessional {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private List<CatalogSchedule> weeklySchedules;
}
