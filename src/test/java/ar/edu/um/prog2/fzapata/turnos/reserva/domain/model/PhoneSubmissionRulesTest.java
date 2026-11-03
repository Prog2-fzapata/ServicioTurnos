package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PhoneSubmissionRulesTest {

    private static ReservationProcess process(ReservationStatus status, String requestEventId) {
        return ReservationProcess.builder().status(status).requestEventId(requestEventId).build();
    }

    @Test
    void canSubmitWhenItWaitsForThePhoneAndTheCatedraAlreadyAskedForIt() {
        assertThat(PhoneSubmissionRules.canSubmit(process(ReservationStatus.WAITING_PHONE, "request-1"))).isTrue();
    }

    // Todavía no llegó el pedido de la cátedra: la app no debe habilitar el formulario
    @Test
    void cannotSubmitBeforeTheCatedraAsked() {
        assertThat(PhoneSubmissionRules.canSubmit(process(ReservationStatus.WAITING_PHONE, null))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = "WAITING_PHONE", mode = EnumSource.Mode.EXCLUDE)
    void cannotSubmitInAnyOtherStatusEvenWithTheRequest(ReservationStatus status) {
        assertThat(PhoneSubmissionRules.canSubmit(process(status, "request-1"))).isFalse();
    }
}
