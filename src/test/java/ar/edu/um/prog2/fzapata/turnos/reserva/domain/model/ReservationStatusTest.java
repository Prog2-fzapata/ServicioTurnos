package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import static ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus.CANCELLED;
import static ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus.CONFIRMED;
import static ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus.EXPIRED;
import static ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus.FAILED;
import static ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus.HELD;
import static ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus.HOLD_REQUESTED;
import static ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus.PHONE_QUEUED;
import static ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus.PHONE_SUBMITTED;
import static ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus.WAITING_PHONE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

// La tabla se escribió a mano desde el diagrama 04 y no desde el código: si alguien cambia una transición, un par falla
class ReservationStatusTest {

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED = Map.of(
            HOLD_REQUESTED, EnumSet.of(HELD, FAILED),
            HELD, EnumSet.of(WAITING_PHONE, EXPIRED, FAILED),
            WAITING_PHONE, EnumSet.of(PHONE_QUEUED, EXPIRED, FAILED),
            // CONFIRMED y WAITING_PHONE desde PHONE_QUEUED: la respuesta de la cátedra puede llegar antes que el ack
            PHONE_QUEUED, EnumSet.of(PHONE_SUBMITTED, CONFIRMED, WAITING_PHONE, EXPIRED, FAILED),
            PHONE_SUBMITTED, EnumSet.of(CONFIRMED, WAITING_PHONE, EXPIRED, FAILED),
            CONFIRMED, EnumSet.of(CANCELLED),
            EXPIRED, EnumSet.noneOf(ReservationStatus.class),
            FAILED, EnumSet.noneOf(ReservationStatus.class),
            CANCELLED, EnumSet.noneOf(ReservationStatus.class));

    static Stream<Arguments> everyPair() {
        return Arrays.stream(ReservationStatus.values()).flatMap(from -> Arrays.stream(ReservationStatus.values())
                .map(target -> Arguments.of(from, target, ALLOWED.get(from).contains(target))));
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @MethodSource("everyPair")
    void everyPairOfStatesFollowsTheDiagram(ReservationStatus from, ReservationStatus target, boolean allowed) {
        assertThat(from.canTransitionTo(target)).isEqualTo(allowed);
    }

    @Test
    void theTableCoversEveryStatus() {
        assertThat(ALLOWED.keySet()).containsExactlyInAnyOrder(ReservationStatus.values());
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"EXPIRED", "FAILED", "CANCELLED"})
    void theFinalStatesAreFinalAndNeverMove(ReservationStatus status) {
        assertThat(status.isFinal()).isTrue();
        assertThat(Arrays.stream(ReservationStatus.values()).filter(status::canTransitionTo)).isEmpty();
    }

    // CONFIRMED todavía puede cancelarse, así que no es final
    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"EXPIRED", "FAILED", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void theOtherStatesAreNotFinal(ReservationStatus status) {
        assertThat(status.isFinal()).isFalse();
    }

    @Test
    void aStatusNeverTransitionsToItself() {
        assertThat(Arrays.stream(ReservationStatus.values()).filter(status -> status.canTransitionTo(status))).isEmpty();
    }

    // Una cancelada sí fue una reserva del usuario; vencida o fallida, no
    @Test
    void theDiscardedAttemptsAreExactlyTheExpiredAndTheFailedOnes() {
        assertThat(ReservationStatus.DISCARDED).containsExactlyInAnyOrder(EXPIRED, FAILED);
    }

    // Solo los abiertos con vencimiento: HOLD_REQUESTED todavía no tiene expiresAt y CONFIRMED ya no vence
    @Test
    void theExpirableStatesAreTheOpenOnesThatHaveAnExpiration() {
        assertThat(ReservationStatus.EXPIRABLE).containsExactlyInAnyOrder(HELD, WAITING_PHONE, PHONE_QUEUED,
                PHONE_SUBMITTED);
    }

    // Todo lo que se puede expirar tiene que poder pasar a EXPIRED según el diagrama
    @ParameterizedTest
    @MethodSource("expirableStatuses")
    void everyExpirableStatusCanMoveToExpired(ReservationStatus status) {
        assertThat(status.canTransitionTo(EXPIRED)).isTrue();
    }

    static Stream<ReservationStatus> expirableStatuses() {
        return ReservationStatus.EXPIRABLE.stream();
    }

    // Desde PHONE_QUEUED el relay puede haber publicado sin anotar el ack: la cátedra pudo confirmar
    @ParameterizedTest
    @EnumSource(ReservationStatus.class)
    void onlyTheStatesAfterQueueingThePhoneMayHaveReachedTheCatedra(ReservationStatus status) {
        assertThat(status.phoneMayHaveReachedCatedra()).isEqualTo(status == PHONE_QUEUED || status == PHONE_SUBMITTED);
    }

    @Test
    void thePhoneSentStatesAreExactlyTheOnesWhereTheCatedraMayHaveConfirmed() {
        assertThat(ReservationStatus.PHONE_SENT).containsExactlyInAnyOrder(PHONE_QUEUED, PHONE_SUBMITTED);
        assertThat(Arrays.stream(ReservationStatus.values()).filter(ReservationStatus::phoneMayHaveReachedCatedra))
                .containsExactlyInAnyOrderElementsOf(ReservationStatus.PHONE_SENT);
    }
}
