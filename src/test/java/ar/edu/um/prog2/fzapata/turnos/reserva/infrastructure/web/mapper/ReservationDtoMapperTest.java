package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationRequest;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.ReservationResponse;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.dto.StartReservationRequest;

class ReservationDtoMapperTest {

    private final ReservationDtoMapper mapper = new ReservationDtoMapper();

    private ReservationProcess process(ReservationStatus status) {
        return ReservationProcess.builder().id(101L).reservationProcessId("process-a92b47d0").holdId("hold-1")
                .userId("42").professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(LocalDate.of(2026, 10, 12)).startTime(LocalTime.of(9, 30))
                .endTime(LocalTime.of(10, 0)).status(status).expiresAt(Instant.parse("2026-10-07T14:44:50Z"))
                .patientFirstName("Juan").patientLastName("Perez").patientPhone("+5492615551234")
                .requestEventId("event-1").reservationId(8251L).failureReason("motivo").cancellationReason("razón")
                .cancelledAt(Instant.parse("2026-10-07T15:00:00Z")).createdAt(Instant.parse("2026-10-07T14:35:00Z"))
                .updatedAt(Instant.parse("2026-10-07T14:35:10Z")).version(2L).build();
    }

    @Test
    void buildsTheRequestWithTheUserFromTheToken() {
        StartReservationRequest body = StartReservationRequest.builder().professionalId(15L)
                .date(LocalDate.of(2026, 10, 12)).startTime(LocalTime.of(9, 30)).build();

        ReservationRequest request = mapper.toRequest(body, "42");

        assertThat(request.userId()).isEqualTo("42");
        assertThat(request.professionalId()).isEqualTo(15L);
        assertThat(request.date()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(request.startTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void mapsEveryFieldOfTheContract() {
        ReservationResponse response = mapper.toResponse(process(ReservationStatus.WAITING_PHONE));

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getReservationProcessId()).isEqualTo("process-a92b47d0");
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.WAITING_PHONE);
        assertThat(response.getProfessionalId()).isEqualTo(15L);
        assertThat(response.getProfessionalFirstName()).isEqualTo("Ana");
        assertThat(response.getProfessionalLastName()).isEqualTo("Gomez");
        assertThat(response.getAppointmentDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(response.getStartTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(response.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(response.getExpiresAt()).isEqualTo(Instant.parse("2026-10-07T14:44:50Z"));
        assertThat(response.getPatientPhone()).isEqualTo("+5492615551234");
        assertThat(response.getFailureReason()).isEqualTo("motivo");
        assertThat(response.getCancellationReason()).isEqualTo("razón");
        assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-10-07T14:35:00Z"));
        assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-10-07T14:35:10Z"));
    }

    @Test
    void theResponseHasNoFieldForTheOwnerNorForTheIdsOfTheCatedra() {
        List<String> fields = Arrays.stream(ReservationResponse.class.getDeclaredFields()).map(Field::getName).toList();

        assertThat(fields).isNotEmpty().doesNotContain("userId", "holdId", "requestEventId", "reservationId",
                "version");
    }

    // El vencimiento solo tiene sentido mientras el proceso sigue abierto
    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"EXPIRED", "FAILED", "CANCELLED"})
    void aFinalProcessHasNoExpiry(ReservationStatus status) {
        assertThat(mapper.toResponse(process(status)).getExpiresAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"EXPIRED", "FAILED", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void anOpenProcessKeepsItsExpiry(ReservationStatus status) {
        assertThat(mapper.toResponse(process(status)).getExpiresAt()).isEqualTo(Instant.parse("2026-10-07T14:44:50Z"));
    }

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toRequest(null, "42")).isNull();
        assertThat(mapper.toResponse(null)).isNull();
    }
}
