package ar.edu.um.prog2.fzapata.turnos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

// En el código de producción los métodos no privados van arriba y los auxiliares (privados) abajo: la clase se lee
// primero como un índice de qué hace y después como cómo lo hace
class MethodOrderTest {

    private static final Pattern ANNOTATION = Pattern
            .compile("@\\w+(\\.\\w+)*(\\s*\\((?:[^()]|\\((?:[^()]|\\([^()]*\\))*\\))*\\))?");
    private static final Pattern TYPE_KEYWORD = Pattern.compile("\\b(class|record|enum|interface)\\b");
    private static final Pattern PRIVATE = Pattern.compile("\\bprivate\\b");
    private static final Pattern WORD = Pattern.compile("\\s*(\\w+)");

    private record Method(String name, boolean isPrivate, int line) {
    }

    private static int endOrLength(String source, int index, int closingLength) {
        return index < 0 ? source.length() : index + closingLength;
    }

    // Si en i empieza un comentario, un string, un char o un text block devuelve dónde termina; si no, devuelve i
    private static int endOfNonCode(String source, int i) {
        if (source.startsWith("\"\"\"", i)) {
            return endOrLength(source, source.indexOf("\"\"\"", i + 3), 3);
        }
        if (source.startsWith("//", i)) {
            return endOrLength(source, source.indexOf('\n', i), 0);
        }
        if (source.startsWith("/*", i)) {
            return endOrLength(source, source.indexOf("*/", i + 2), 2);
        }
        char c = source.charAt(i);
        if (c == '"' || c == '\'') {
            int j = i + 1;
            while (j < source.length() && source.charAt(j) != c) {
                j += source.charAt(j) == '\\' ? 2 : 1;
            }
            return Math.min(j + 1, source.length());
        }
        return i;
    }

    // Reemplaza comentarios y strings por espacios, conservando los saltos de línea: las llaves y paréntesis que
    // quedan son las del código
    private static String withoutCommentsAndStrings(String source) {
        StringBuilder clean = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            int end = endOfNonCode(source, i);
            if (end == i) {
                clean.append(source.charAt(i));
                i++;
            } else {
                for (int k = i; k < end; k++) {
                    clean.append(source.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = end;
            }
        }
        return clean.toString();
    }

    // Índice siguiente a la llave que cierra el bloque que se abre en open
    private static int endOfBlock(String clean, int open) {
        int depth = 0;
        for (int i = open; i < clean.length(); i++) {
            if (clean.charAt(i) == '{') {
                depth++;
            } else if (clean.charAt(i) == '}' && --depth == 0) {
                return i + 1;
            }
        }
        return clean.length();
    }

    private static int skipWhitespace(String clean, int from) {
        int i = from;
        while (i < clean.length() && Character.isWhitespace(clean.charAt(i))) {
            i++;
        }
        return i;
    }

    // La llave que abre el cuerpo del tipo, saltando los paréntesis de la cabecera de un record
    private static int bodyStart(String clean, int from) {
        int parens = 0;
        for (int i = from; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == '(') {
                parens++;
            } else if (c == ')') {
                parens--;
            } else if (c == '{' && parens == 0) {
                return i;
            }
        }
        return clean.length();
    }

    // Un miembro es un método si tiene tipo y nombre antes del primer paréntesis; los campos, los constructores, las
    // constantes de un enum y los tipos anidados no lo son
    private static Method asMethod(String header, String typeName, int line) {
        String stripped = ANNOTATION.matcher(header).replaceAll(" ");
        int paren = stripped.indexOf('(');
        if (paren < 0) {
            return null;
        }
        String before = stripped.substring(0, paren);
        if (before.contains("=") || TYPE_KEYWORD.matcher(before).find()) {
            return null;
        }
        String[] tokens = before.trim().split("\\s+");
        String name = tokens[tokens.length - 1];
        if (tokens.length < 2 || name.equals(typeName)) {
            return null;
        }
        return new Method(name, PRIVATE.matcher(before).find(), line);
    }

    // Los métodos del tipo de primer nivel, en el orden en que están escritos
    private static List<Method> methodsOf(String source) {
        String clean = withoutCommentsAndStrings(source);
        Matcher type = TYPE_KEYWORD.matcher(clean);
        if (!type.find()) {
            return List.of();
        }
        Matcher name = WORD.matcher(clean);
        name.find(type.end());
        List<Method> methods = new ArrayList<>();
        int i = bodyStart(clean, name.end()) + 1;
        while (true) {
            i = skipWhitespace(clean, i);
            if (i >= clean.length() || clean.charAt(i) == '}') {
                return methods;
            }
            int start = i;
            int headerEnd = -1;
            int parens = 0;
            boolean assignment = false;
            while (i < clean.length()) {
                char c = clean.charAt(i);
                if (c == '(') {
                    parens++;
                } else if (c == ')') {
                    parens--;
                } else if (parens == 0 && c == '=') {
                    assignment = true;
                } else if (parens == 0 && (c == ';' || c == '{')) {
                    headerEnd = headerEnd < 0 ? i : headerEnd;
                    if (c == ';') {
                        i++;
                        break;
                    }
                    i = endOfBlock(clean, i);
                    if (!assignment) {
                        break;
                    }
                    continue;
                }
                i++;
            }
            int line = (int) clean.substring(0, start).chars().filter(c -> c == '\n').count() + 1;
            Method method = asMethod(clean.substring(start, headerEnd < 0 ? clean.length() : headerEnd),
                    name.group(1), line);
            if (method != null) {
                methods.add(method);
            }
        }
    }

    // Los métodos privados que están escritos antes del último método no privado
    private static List<String> privatesAboveANonPrivate(String source) {
        List<Method> methods = methodsOf(source);
        int lastNonPrivate = methods.stream().filter(method -> !method.isPrivate()).mapToInt(Method::line).max()
                .orElse(0);
        return methods.stream().filter(method -> method.isPrivate() && method.line() < lastNonPrivate)
                .map(method -> method.name() + " (línea " + method.line() + ")").toList();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void noClassDefinesAPrivateMethodBeforeANonPrivateOne() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<String> misplaced = files.filter(file -> file.toString().endsWith(".java"))
                    .flatMap(file -> privatesAboveANonPrivate(read(file)).stream()
                            .map(method -> file + ": " + method))
                    .toList();

            assertThat(misplaced).as("los métodos privados van después de los no privados").isEmpty();
        }
    }

    @Test
    void flagsAPrivateMethodDefinedBeforeAPublicOne() {
        String source = """
                class Sample {
                    private int helper() {
                        return 1;
                    }

                    public int main() {
                        return helper();
                    }
                }
                """;

        assertThat(privatesAboveANonPrivate(source)).containsExactly("helper (línea 2)");
    }

    @Test
    void flagsAPrivateMethodBetweenTwoPublicOnes() {
        String source = """
                class Sample {
                    public int first() {
                        return helper();
                    }

                    private <T> T helper() {
                        return null;
                    }

                    int second() {
                        return 2;
                    }
                }
                """;

        assertThat(privatesAboveANonPrivate(source)).containsExactly("helper (línea 6)");
    }

    @Test
    void acceptsPublicMethodsFirstAndPrivateOnesAfter() {
        String source = """
                class Sample {
                    public int main() {
                        return helper();
                    }

                    private int helper() {
                        return 1;
                    }
                }
                """;

        assertThat(privatesAboveANonPrivate(source)).isEmpty();
    }

    @Test
    void ignoresFieldsConstructorsNestedTypesCommentsAndStrings() {
        String source = """
                class Sample {
                    private static final String BRACE = "{";
                    // private void fake() {}
                    private final Object lazy = new Object() { };

                    Sample() {
                    }

                    private record Pair(int a, int b) {
                    }

                    public void main() {
                        String text = "private void other() {";
                    }

                    private void helper() {
                    }
                }
                """;

        assertThat(privatesAboveANonPrivate(source)).isEmpty();
    }
}
