package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.util.regex.Pattern;

// La regla de la cátedra para el teléfono: se quitan espacios, guiones, paréntesis y puntos, y queda un + opcional
// seguido de 7 a 15 dígitos. Se envía tal cual quedó, sin completar el código de país ni quitar el 0 o el 15
public final class PhoneNumber {

    private static final Pattern SEPARATORS = Pattern.compile("[ \\-().]");
    private static final Pattern VALID = Pattern.compile("\\+?[0-9]{7,15}");

    private PhoneNumber() {
    }

    private static String clean(String raw) {
        return SEPARATORS.matcher(raw).replaceAll("");
    }

    public static boolean isValid(String raw) {
        return raw != null && VALID.matcher(clean(raw)).matches();
    }

    public static String normalize(String raw) {
        if (!isValid(raw)) {
            throw new IllegalArgumentException("El teléfono no cumple el formato");
        }
        return clean(raw);
    }
}
