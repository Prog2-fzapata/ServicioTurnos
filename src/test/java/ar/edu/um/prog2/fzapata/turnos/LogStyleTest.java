package ar.edu.um.prog2.fzapata.turnos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

// Todas las líneas de log se escriben igual: log.atInfo().setMessage("...").addKeyValue(...).log(). Así los datos salen
// como campos del JSON y no dentro de la frase, y el log no mezcla dos estilos
class LogStyleTest {

    private static final Pattern CLASSIC_CALL = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(");

    private static List<String> classicCallsIn(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return IntStream.range(0, lines.size()).filter(i -> CLASSIC_CALL.matcher(lines.get(i)).find())
                    .mapToObj(i -> file + ":" + (i + 1) + " " + lines.get(i).trim()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void everyLogLineUsesTheFluentApi() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<String> classicCalls = files.filter(file -> file.toString().endsWith(".java"))
                    .flatMap(file -> classicCallsIn(file).stream()).toList();

            assertThat(classicCalls).as("usar log.atInfo().setMessage(...).addKeyValue(...).log()").isEmpty();
        }
    }
}
