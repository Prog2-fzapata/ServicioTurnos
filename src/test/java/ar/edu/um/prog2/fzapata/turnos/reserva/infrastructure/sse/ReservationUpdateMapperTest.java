package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;

class ReservationUpdateMapperTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-11-04T13:10:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-11-04T13:00:05Z");

    private final ReservationUpdateMapper mapper = new ReservationUpdateMapper();

    private static ReservationProcess process(ReservationStatus status) {
        return ReservationProcess.builder().id(101L).userId("42").status(status).expiresAt(EXPIRES_AT)
                .updatedAt(UPDATED_AT).version(5L).build();
    }

    @Test
    void copiesTheSummaryOfAnOpenProcess() {
        ReservationUpdate update = mapper.toUpdate(process(ReservationStatus.HELD));

        assertThat(update).isEqualTo(new ReservationUpdate(101L, ReservationStatus.HELD, EXPIRES_AT, false,
                UPDATED_AT));
    }

    // Igual que en Reservation: terminado el proceso, el vencimiento ya no significa nada
    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"EXPIRED", "FAILED", "CANCELLED"})
    void aFinalProcessHasNoExpiration(ReservationStatus finalStatus) {
        assertThat(mapper.toUpdate(process(finalStatus)).expiresAt()).isNull();
    }

    @Test
    void canSubmitPhoneOnlyAfterTheCatedraAskedForIt() {
        ReservationProcess waiting = process(ReservationStatus.WAITING_PHONE);
        assertThat(mapper.toUpdate(waiting).canSubmitPhone()).isFalse();

        waiting.setRequestEventId("request-event-8a31");
        assertThat(mapper.toUpdate(waiting).canSubmitPhone()).isTrue();
    }

    @Test
    void theEventIdIsTheProcessIdAndItsVersion() {
        assertThat(mapper.toEventId(process(ReservationStatus.HELD))).isEqualTo("101-5");
    }

    @Test
    void aMissingProcessMapsToNothing() {
        assertThat(mapper.toUpdate(null)).isNull();
    }
}
