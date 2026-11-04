package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberTest {

    // Lo que escribe el usuario y lo que se envía: solo se quitan los separadores, nada se completa ni se corrige
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            +5492616159550 | +5492616159550
            +542616159550 | +542616159550
            2616159550 | 2616159550
            +54 9 261 615-9550 | +5492616159550
            (0261) 615.9550 | 02616159550
            261 615 9550 | 2616159550
            15-6159550 | 156159550
            0054 9 261 6159550 | 00549261 6159550
            1234567 | 1234567
            +1234567 | +1234567
            123456789012345 | 123456789012345
            +123456789012345 | +123456789012345
            """)
    void acceptsTheFormatsPeopleUseAndOnlyRemovesTheSeparators(String raw, String expected) {
        assertThat(PhoneNumber.isValid(raw)).isTrue();
        assertThat(PhoneNumber.normalize(raw)).isEqualTo(expected.replace(" ", ""));
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
            "123456",              // 6 dígitos: muy corto
            "1234567890123456",    // 16 dígitos: muy largo
            "+1234567890123456",
            "261-ABC-9550",        // letras
            "54+92616159550",      // el + solo puede ir al principio
            "++5492616159550",
            "+",
            "(+54) 261 615 9550 x", // texto de más
            "261/615/9550",        // la barra no es un separador
            "261_615_9550",
            "٢٦١٦١٥٩٥٥٠",           // dígitos que no son 0-9
            "   "})
    void rejectsWhatDoesNotFollowTheRule(String raw) {
        assertThat(PhoneNumber.isValid(raw)).isFalse();
        assertThatThrownBy(() -> PhoneNumber.normalize(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void aMissingPhoneIsNotValid(String raw) {
        assertThat(PhoneNumber.isValid(raw)).isFalse();
    }

    // Los separadores no cuentan para el largo: 15 dígitos con guiones y espacios es válido
    @Test
    void theSeparatorsDoNotCountForTheLength() {
        assertThat(PhoneNumber.isValid("+12 34-56 (78) 9.0 12 34 5")).isTrue();
    }
}
