package ar.edu.um.prog2.fzapata.turnos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

class PaginationHeadersTest {

    private static final String BASE = "http://localhost:8081/api/professionals";

    private HttpHeaders headers(String query, int page, int size, long total) {
        return PaginationHeaders.create(UriComponentsBuilder.fromUriString(BASE + query), page, size, total);
    }

    private String link(HttpHeaders headers) {
        return headers.getFirst(HttpHeaders.LINK);
    }

    @Test
    void reportsTheTotalNumberOfElementsAndNotTheOnesOfThePage() {
        assertThat(headers("?page=0&size=20", 0, 20, 134).getFirst("X-Total-Count")).isEqualTo("134");
    }

    @Test
    void theFirstPageHasNextLastAndFirstButNoPrev() {
        String link = link(headers("?page=0&size=2", 0, 2, 5));

        assertThat(link).contains("<" + BASE + "?page=1&size=2>; rel=\"next\"")
                .contains("<" + BASE + "?page=2&size=2>; rel=\"last\"")
                .contains("<" + BASE + "?page=0&size=2>; rel=\"first\"")
                .doesNotContain("rel=\"prev\"");
    }

    @Test
    void aMiddlePageHasAllFourLinks() {
        String link = link(headers("?page=1&size=2", 1, 2, 7));

        assertThat(link).contains("<" + BASE + "?page=2&size=2>; rel=\"next\"")
                .contains("<" + BASE + "?page=0&size=2>; rel=\"prev\"")
                .contains("<" + BASE + "?page=3&size=2>; rel=\"last\"")
                .contains("<" + BASE + "?page=0&size=2>; rel=\"first\"");
    }

    @Test
    void theLastPageHasNoNext() {
        String link = link(headers("?page=2&size=2", 2, 2, 5));

        assertThat(link).doesNotContain("rel=\"next\"").contains("rel=\"prev\"")
                .contains("<" + BASE + "?page=2&size=2>; rel=\"last\"");
    }

    @Test
    void anExactMultipleDoesNotInventAnEmptyLastPage() {
        assertThat(link(headers("?page=0&size=2", 0, 2, 4))).contains("<" + BASE + "?page=1&size=2>; rel=\"last\"");
    }

    @Test
    void noElementsStillPointsToPageZero() {
        String link = link(headers("?page=0&size=20", 0, 20, 0));

        assertThat(link).doesNotContain("rel=\"next\"").doesNotContain("rel=\"prev\"")
                .contains("<" + BASE + "?page=0&size=20>; rel=\"last\"")
                .contains("<" + BASE + "?page=0&size=20>; rel=\"first\"");
    }

    // El enlace siguiente debe llevar a la misma búsqueda: mismos filtros y mismo orden
    @Test
    void keepsTheFiltersAndTheSortOfTheCurrentRequest() {
        String link = link(headers("?name=ana&enabled=true&sort=lastName,desc&page=0&size=2", 0, 2, 5));

        assertThat(link).contains("name=ana").contains("enabled=true").contains("sort=lastName,desc")
                .contains("page=1&size=2");
    }

    @Test
    void addsThePageAndTheSizeWhenTheRequestDidNotHaveThem() {
        assertThat(link(headers("", 0, 20, 50))).contains("<" + BASE + "?page=1&size=20>; rel=\"next\"");
    }

    // La consulta del pedido ya viene codificada: no se debe codificar de nuevo (%20 no puede pasar a %2520)
    @Test
    void keepsTheEncodedCharactersOfTheFiltersAsTheyCame() {
        String link = link(headers("?name=ana%20g%C3%B3mez", 0, 2, 5));

        assertThat(link).contains("name=ana%20g%C3%B3mez").doesNotContain("%25");
    }
}
