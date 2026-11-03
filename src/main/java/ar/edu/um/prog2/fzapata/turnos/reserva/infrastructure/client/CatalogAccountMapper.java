package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogAccount;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CatalogAccountResponse;

@Component
public class CatalogAccountMapper {

    public CatalogAccount toDomainModel(CatalogAccountResponse response) {
        if (response == null) return null;
        return CatalogAccount.builder()
                .firstName(response.getFirstName())
                .lastName(response.getLastName())
                .build();
    }
}
