package ar.edu.um.prog2.fzapata.turnos.reserva.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidSlotException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalDisabledException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ProfessionalNotFoundException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.SlotUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;

class HoldRejectionTranslatorTest {

    private static final Long PROFESSIONAL_ID = 15L;

    static Stream<Arguments> translations() {
        return Stream.of(
                Arguments.of(CatedraRejection.SLOT_ALREADY_HELD, SlotUnavailableException.class,
                        "El horario ya no está disponible"),
                Arguments.of(CatedraRejection.SLOT_ALREADY_RESERVED, SlotUnavailableException.class,
                        "El horario ya no está disponible"),
                Arguments.of(CatedraRejection.PROFESSIONAL_DISABLED, ProfessionalDisabledException.class,
                        "La cátedra informa deshabilitado al profesional 15"),
                Arguments.of(CatedraRejection.PROFESSIONAL_NOT_FOUND, ProfessionalNotFoundException.class,
                        "No existe el profesional 15"),
                Arguments.of(CatedraRejection.INVALID_SLOT, InvalidSlotException.class,
                        "La cátedra no reconoce el horario en la agenda del profesional 15"),
                Arguments.of(CatedraRejection.HOLD_NOT_FOUND, CatedraResponseException.class,
                        "La cátedra rechazó el hold con HOLD_NOT_FOUND"),
                Arguments.of(CatedraRejection.HOLD_EXPIRED, CatedraResponseException.class,
                        "La cátedra rechazó el hold con HOLD_EXPIRED"),
                Arguments.of(CatedraRejection.RESERVATION_PROCESS_ALREADY_CONFIRMED, CatedraResponseException.class,
                        "La cátedra rechazó el hold con RESERVATION_PROCESS_ALREADY_CONFIRMED"),
                Arguments.of(CatedraRejection.RESERVATION_NOT_FOUND, CatedraResponseException.class,
                        "La cátedra rechazó el hold con RESERVATION_NOT_FOUND"),
                Arguments.of(CatedraRejection.RESERVATION_NOT_CANCELLABLE, CatedraResponseException.class,
                        "La cátedra rechazó el hold con RESERVATION_NOT_CANCELLABLE"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("translations")
    void translatesEveryRejectionToItsException(CatedraRejection rejection,
            Class<? extends RuntimeException> type, String message) {
        assertThat(HoldRejectionTranslator.translate(rejection, PROFESSIONAL_ID)).isExactlyInstanceOf(type)
                .hasMessage(message);
    }

    // Un motivo nuevo no se puede traducir a un desajuste sin que alguien lo decida: este test obliga a listarlo
    @Test
    void theTableListsEveryRejection() {
        assertThat(translations().map(arguments -> arguments.get()[0]).toList())
                .containsExactlyInAnyOrder((Object[]) CatedraRejection.values());
    }

    @Test
    void aRejectionWithoutFunctionalMeaningIsAContractMismatchWithoutCause() {
        assertThat(HoldRejectionTranslator.translate(CatedraRejection.HOLD_EXPIRED, PROFESSIONAL_ID)).hasNoCause();
    }
}
