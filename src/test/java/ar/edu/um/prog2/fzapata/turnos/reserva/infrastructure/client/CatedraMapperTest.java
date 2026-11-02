package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.AppointmentResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.CancelResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.ConfirmResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.HoldResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.OccupancyListResponse;

class CatedraMapperTest {

    private final CatedraMapper mapper = new CatedraMapper();

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toDomainModel((HoldResponse) null)).isNull();
        assertThat(mapper.toDomainModel((ConfirmResponse) null)).isNull();
        assertThat(mapper.toDomainModel((AppointmentResponse) null)).isNull();
        assertThat(mapper.toDomainModel((CancelResponse) null)).isNull();
    }

    @Test
    void aMissingResponseOrListOfOccupanciesIsAnEmptyList() {
        assertThat(mapper.toDomainModel((OccupancyListResponse) null)).isEmpty();
        assertThat(mapper.toDomainModel(OccupancyListResponse.builder().build())).isEmpty();
        assertThat(mapper.toDomainModel(OccupancyListResponse.builder().occupancies(List.of()).build())).isEmpty();
    }
}
