package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// La cuenta del usuario autenticado según catálogo. Nombre y apellido son opcionales en el registro: pueden faltar
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogAccount {

    private String firstName;
    private String lastName;
}
