package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity.ReservationProcessEntity;

class ReservationProcessMapperTest {

    private final ReservationProcessMapper mapper = new ReservationProcessMapper();

    // Cada campo con un valor distinto: un par cruzado o un campo olvidado rompe la comparación
    private ReservationProcess fullProcess() {
        return ReservationProcess.builder().id(7L).reservationProcessId("process-1").holdId("hold-1").userId("42")
                .professionalId(15L).professionalFirstName("Ana").professionalLastName("Gomez")
                .appointmentDate(LocalDate.of(2026, 11, 9)).startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0))
                .status(ReservationStatus.PHONE_SUBMITTED).expiresAt(Instant.parse("2026-11-02T14:10:00Z"))
                .patientFirstName("Juan").patientLastName("Perez").patientPhone("+5492615551234")
                .requestEventId("event-1").reservationId(99L).failureReason("motivo").cancellationReason("razón")
                .cancelledAt(Instant.parse("2026-11-02T15:00:00Z")).createdAt(Instant.parse("2026-11-02T13:00:00Z"))
                .updatedAt(Instant.parse("2026-11-02T14:00:00Z")).version(5L).build();
    }

    @Test
    void mapsEveryFieldToTheEntity() {
        ReservationProcessEntity entity = mapper.toEntity(fullProcess());

        assertThat(entity.getId()).isEqualTo(7L);
        assertThat(entity.getReservationProcessId()).isEqualTo("process-1");
        assertThat(entity.getHoldId()).isEqualTo("hold-1");
        assertThat(entity.getUserId()).isEqualTo("42");
        assertThat(entity.getProfessionalId()).isEqualTo(15L);
        assertThat(entity.getProfessionalFirstName()).isEqualTo("Ana");
        assertThat(entity.getProfessionalLastName()).isEqualTo("Gomez");
        assertThat(entity.getAppointmentDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(entity.getStartTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(entity.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(entity.getStatus()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
        assertThat(entity.getExpiresAt()).isEqualTo(Instant.parse("2026-11-02T14:10:00Z"));
        assertThat(entity.getPatientFirstName()).isEqualTo("Juan");
        assertThat(entity.getPatientLastName()).isEqualTo("Perez");
        assertThat(entity.getPatientPhone()).isEqualTo("+5492615551234");
        assertThat(entity.getRequestEventId()).isEqualTo("event-1");
        assertThat(entity.getReservationId()).isEqualTo(99L);
        assertThat(entity.getFailureReason()).isEqualTo("motivo");
        assertThat(entity.getCancellationReason()).isEqualTo("razón");
        assertThat(entity.getCancelledAt()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
        assertThat(entity.getCreatedAt()).isEqualTo(Instant.parse("2026-11-02T13:00:00Z"));
        assertThat(entity.getUpdatedAt()).isEqualTo(Instant.parse("2026-11-02T14:00:00Z"));
        // Sin la versión no hay bloqueo optimista: el guardado compararía contra nada
        assertThat(entity.getVersion()).isEqualTo(5L);
    }

    @Test
    void theRoundTripKeepsEveryField() {
        ReservationProcess process = fullProcess();

        assertThat(mapper.toDomainModel(mapper.toEntity(process))).usingRecursiveComparison().isEqualTo(process);
    }

    @Test
    void aNewProcessHasNoIdNorVersion() {
        ReservationProcess created = ReservationProcess.builder().status(ReservationStatus.HOLD_REQUESTED).build();

        ReservationProcessEntity entity = mapper.toEntity(created);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getVersion()).isNull();
        assertThat(entity.getReservationProcessId()).isNull();
        assertThat(entity.getHoldId()).isNull();
    }

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomainModel(null)).isNull();
    }
}
