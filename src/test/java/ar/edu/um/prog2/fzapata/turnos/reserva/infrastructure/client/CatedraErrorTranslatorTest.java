package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraAuthenticationException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraNotReachedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraRejectedException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraResponseException;
import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.CatedraUnavailableException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.CatedraRejection;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.client.dto.ProblemResponse;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// La traducción de las fallas del cliente HTTP, probada sin servidor: el RestClient la usa con errores como los de acá
class CatedraErrorTranslatorTest {

    private static final String SECRET_BODY = "cuerpo-secreto";

    private final Logger translatorLogger = (Logger) LoggerFactory.getLogger(CatedraErrorTranslator.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        translatorLogger.setLevel(Level.DEBUG);
        appender.start();
        translatorLogger.addAppender(appender);
    }

    @AfterEach
    void releaseTheLog() {
        translatorLogger.detachAppender(appender);
        appender.stop();
    }

    // Un error HTTP como el que arma el RestClient: con el status, el cuerpo en el mensaje y un code legible
    private static RestClientResponseException httpError(int status, String code) {
        RestClientResponseException error = unreadableHttpError(status);
        error.setBodyConvertFunction(type -> new ProblemResponse(code));
        return error;
    }

    // Sin función de conversión: leer el cuerpo falla, como con un cuerpo que no es JSON
    private static RestClientResponseException unreadableHttpError(int status) {
        return new RestClientResponseException(SECRET_BODY, status, "texto", new HttpHeaders(),
                SECRET_BODY.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    // Los campos de una línea de log (los que salen como campos del JSON); sin campos, logback devuelve null
    private static Map<String, Object> fieldsOf(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
        }
        return fields;
    }

    static Stream<Arguments> matrix() {
        return Stream.of(
                Arguments.of(409, "SLOT_ALREADY_HELD", CatedraRejection.SLOT_ALREADY_HELD),
                Arguments.of(409, "SLOT_ALREADY_RESERVED", CatedraRejection.SLOT_ALREADY_RESERVED),
                Arguments.of(400, "PROFESSIONAL_DISABLED", CatedraRejection.PROFESSIONAL_DISABLED),
                Arguments.of(404, "PROFESSIONAL_NOT_FOUND", CatedraRejection.PROFESSIONAL_NOT_FOUND),
                Arguments.of(400, "INVALID_SLOT", CatedraRejection.INVALID_SLOT),
                Arguments.of(404, "HOLD_NOT_FOUND", CatedraRejection.HOLD_NOT_FOUND),
                Arguments.of(404, "HOLD_EXPIRED", CatedraRejection.HOLD_EXPIRED),
                Arguments.of(409, "RESERVATION_PROCESS_ALREADY_CONFIRMED",
                        CatedraRejection.RESERVATION_PROCESS_ALREADY_CONFIRMED),
                Arguments.of(404, "RESERVATION_NOT_FOUND", CatedraRejection.RESERVATION_NOT_FOUND),
                Arguments.of(409, "RESERVATION_NOT_CANCELLABLE", CatedraRejection.RESERVATION_NOT_CANCELLABLE));
    }

    static Stream<IOException> connectionsThatNeverWereEstablished() {
        return Stream.of(new ConnectException("refused"), new HttpConnectTimeoutException("t"),
                new IOException("envoltorio", new ConnectException("refused")));
    }

    static Stream<ResourceAccessException> failuresAfterSending() {
        return Stream.of(new ResourceAccessException("x", new HttpTimeoutException("read")),
                new ResourceAccessException("x", new IOException("corte")), new ResourceAccessException("x"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("matrix")
    void aFunctionalErrorIsARejectionWithItsReason(int status, String code, CatedraRejection reason) {
        assertThat(CatedraErrorTranslator.translate(httpError(status, code)))
                .isInstanceOfSatisfying(CatedraRejectedException.class,
                        rejected -> assertThat(rejected.getRejection()).isEqualTo(reason));
    }

    // Un motivo nuevo de la cátedra no puede quedar sin su entrada en la matriz
    @Test
    void theMatrixListsEveryRejection() {
        assertThat(matrix().map(arguments -> arguments.get()[2]).toList())
                .containsExactlyInAnyOrder((Object[]) CatedraRejection.values());
    }

    // La matriz decide por status y code juntos: el mismo code con otro status es un desajuste
    @ParameterizedTest
    @CsvSource({"400, SLOT_ALREADY_HELD", "409, HOLD_EXPIRED", "422, INVALID_SLOT"})
    void aKnownCodeWithAnotherStatusIsNotARejection(int status, String code) {
        assertThat(CatedraErrorTranslator.translate(httpError(status, code)))
                .isExactlyInstanceOf(CatedraResponseException.class)
                .hasMessage("La cátedra respondió " + status + " " + code);
    }

    @ParameterizedTest
    @CsvSource({"500", "502", "503"})
    void aServerErrorIsUnavailableWithoutTheOriginalAsCause(int status) {
        assertThat(CatedraErrorTranslator.translate(httpError(status, "ANY_CODE")))
                .isExactlyInstanceOf(CatedraUnavailableException.class)
                .hasMessage("La cátedra no está disponible (" + status + ")").hasNoCause();
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(fieldsOf(event)).containsOnly(Map.entry("status", status));
        });
    }

    @ParameterizedTest
    @CsvSource({"401", "403"})
    void aRejectedIdentityIsAnAuthenticationErrorWithoutTheOriginalAsCause(int status) {
        assertThat(CatedraErrorTranslator.translate(httpError(status, "HOLD_OWNER_MISMATCH")))
                .isExactlyInstanceOf(CatedraAuthenticationException.class)
                .hasMessage("La cátedra rechazó el JWT técnico (" + status + "): revisar CATEDRA_JWT").hasNoCause();
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(fieldsOf(event)).containsOnly(Map.entry("status", status));
        });
    }

    @Test
    void anUnexpectedErrorKeepsTheStatusAndTheCodeButNotTheBody() {
        assertThat(CatedraErrorTranslator.translate(httpError(400, "VALIDATION_ERROR")))
                .isExactlyInstanceOf(CatedraResponseException.class).hasMessage("La cátedra respondió 400 VALIDATION_ERROR")
                .hasMessageNotContaining(SECRET_BODY).hasNoCause();
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(fieldsOf(event)).containsOnly(Map.entry("status", 400), Map.entry("code", "VALIDATION_ERROR"));
        });
    }

    // El code viene de afuera: si no tiene la forma de un code, no se copia a un mensaje ni a un log
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"inyectado ERROR falso", "minúsculas", "CON-GUION", "''"})
    void aCodeThatDoesNotLookLikeACodeIsDropped(String code) {
        assertThat(CatedraErrorTranslator.translate(httpError(400, code)))
                .isExactlyInstanceOf(CatedraResponseException.class).hasMessage("La cátedra respondió 400");
        assertThat(appender.list).singleElement()
                .satisfies(event -> assertThat(fieldsOf(event)).containsEntry("code", null));
    }

    @Test
    void aCodeOfTheMaximumLengthIsKeptAndOneLongerIsDropped() {
        String longest = "A".repeat(60);

        assertThat(CatedraErrorTranslator.translate(httpError(400, longest)))
                .hasMessage("La cátedra respondió 400 " + longest);
        assertThat(CatedraErrorTranslator.translate(httpError(400, longest + "A")))
                .hasMessage("La cátedra respondió 400");
    }

    @Test
    void aBodyThatCannotBeReadHasNoCode() {
        assertThat(CatedraErrorTranslator.translate(unreadableHttpError(404)))
                .isExactlyInstanceOf(CatedraResponseException.class).hasMessage("La cátedra respondió 404");
    }

    @Test
    void aMissingCodeHasNoCode() {
        assertThat(CatedraErrorTranslator.translate(httpError(404, null)))
                .isExactlyInstanceOf(CatedraResponseException.class).hasMessage("La cátedra respondió 404");
    }

    @ParameterizedTest
    @MethodSource("connectionsThatNeverWereEstablished")
    void aConnectionThatNeverWasEstablishedIsNotReached(IOException cause) {
        ResourceAccessException error = new ResourceAccessException("x", cause);

        assertThat(CatedraErrorTranslator.translate(error)).isExactlyInstanceOf(CatedraNotReachedException.class)
                .hasMessage("No se pudo conectar con la cátedra").hasCause(error);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(fieldsOf(event)).isEmpty();
        });
    }

    // Un timeout de lectura o un corte después de enviar: el pedido pudo haber llegado
    @ParameterizedTest
    @MethodSource("failuresAfterSending")
    void aFailureAfterSendingIsUnavailableAndTheRequestMayHaveArrived(ResourceAccessException error) {
        assertThat(CatedraErrorTranslator.translate(error)).isExactlyInstanceOf(CatedraUnavailableException.class)
                .hasMessage("La cátedra no respondió").hasCause(error);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(fieldsOf(event)).containsOnly(Map.entry("exception", "ResourceAccessException"));
        });
    }

    @Test
    void aResponseThatCannotBeConvertedIsAResponseErrorWithItsCause() {
        RestClientException error = new RestClientException("json roto");

        assertThat(CatedraErrorTranslator.translate(error)).isExactlyInstanceOf(CatedraResponseException.class)
                .hasMessage("La respuesta de la cátedra no se pudo leer").hasCause(error);
    }

    // ConnectException (rechazada) o HttpConnectTimeoutException: no se llegó a enviar nada
    @Test
    void aRefusedOrTimedOutConnectionMeansThatNothingWasSent() {
        assertThat(CatedraErrorTranslator
                .neverConnected(new ResourceAccessException("x", new ConnectException("refused")))).isTrue();
        assertThat(CatedraErrorTranslator
                .neverConnected(new ResourceAccessException("x", new HttpConnectTimeoutException("t")))).isTrue();
        // La causa puede venir envuelta
        assertThat(CatedraErrorTranslator.neverConnected(new ResourceAccessException("x",
                new IOException("envoltorio", new ConnectException("refused"))))).isTrue();
    }

    @Test
    void anyOtherFailureMeansThatTheRequestMayHaveArrived() {
        assertThat(CatedraErrorTranslator
                .neverConnected(new ResourceAccessException("x", new HttpTimeoutException("read")))).isFalse();
        assertThat(CatedraErrorTranslator.neverConnected(new ResourceAccessException("x", new IOException("corte"))))
                .isFalse();
        assertThat(CatedraErrorTranslator.neverConnected(new ResourceAccessException("x"))).isFalse();
    }
}
