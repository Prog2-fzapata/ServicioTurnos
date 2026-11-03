package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatalogAccount;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CatalogAccountResponse;

class CatalogAccountMapperTest {

    private final CatalogAccountMapper mapper = new CatalogAccountMapper();

    @Test
    void mapsTheNameAndTheSurname() {
        CatalogAccount account = mapper
                .toDomainModel(CatalogAccountResponse.builder().firstName("Juan").lastName("Perez").build());

        assertThat(account.getFirstName()).isEqualTo("Juan");
        assertThat(account.getLastName()).isEqualTo("Perez");
    }

    @Test
    void missingNamesStayNull() {
        CatalogAccount account = mapper.toDomainModel(new CatalogAccountResponse());

        assertThat(account.getFirstName()).isNull();
        assertThat(account.getLastName()).isNull();
    }

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toDomainModel(null)).isNull();
    }
}
