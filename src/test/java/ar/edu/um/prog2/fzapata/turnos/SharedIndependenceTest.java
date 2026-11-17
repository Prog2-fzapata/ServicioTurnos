package ar.edu.um.prog2.fzapata.turnos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

// shared es lo transversal (propiedades, seguridad, formato de errores) y no conoce ningún slice: quien depende de
// quién va en un solo sentido, de reserva hacia shared. Lo que arma objetos del dominio es de reserva
class SharedIndependenceTest {

    private static final String SLICE_IMPORT = "import ar.edu.um.prog2.fzapata.turnos.reserva.";

    private static List<String> sliceImportsIn(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return IntStream.range(0, lines.size()).filter(i -> lines.get(i).startsWith(SLICE_IMPORT))
                    .mapToObj(i -> file + ":" + (i + 1) + " " + lines.get(i)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> sliceImportsUnder(String directory) throws IOException {
        try (Stream<Path> files = Files.walk(Path.of(directory))) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .flatMap(file -> sliceImportsIn(file).stream()).toList();
        }
    }

    @Test
    void sharedDoesNotImportFromAnySlice() throws IOException {
        assertThat(sliceImportsUnder("src/main/java/ar/edu/um/prog2/fzapata/turnos/shared"))
                .as("shared no importa de reserva: lo que usa el dominio va en reserva/infrastructure/config")
                .isEmpty();
    }

    @Test
    void theTestsOfSharedDoNotImportFromAnySliceEither() throws IOException {
        assertThat(sliceImportsUnder("src/test/java/ar/edu/um/prog2/fzapata/turnos/shared")).isEmpty();
    }
}
