package ar.edu.um.prog2.fzapata.turnos.shared.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

// Cabeceras de paginación al estilo JHipster: la respuesta es un arreglo y el total y los enlaces van aparte.
// X-Total-Count trae el total de elementos y Link los enlaces a la primera, anterior, siguiente y última página,
// con los mismos filtros y orden del pedido actual
public final class PaginationHeaders {

    public static final String TOTAL_COUNT = "X-Total-Count";

    private PaginationHeaders() {
    }

    // La consulta del pedido actual ya viene codificada (ana%20g%C3%B3mez): codificarla otra vez la dañaría
    private static String link(UriComponentsBuilder base, int page, int size, String rel) {
        String uri = base.cloneBuilder().replaceQueryParam("page", page).replaceQueryParam("size", size)
                .build().toUriString();
        return "<" + uri + ">; rel=\"" + rel + "\"";
    }

    // page: la página actual, desde 0. La última es la 0 si no hay elementos
    public static HttpHeaders create(UriComponentsBuilder currentRequest, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int lastPage = Math.max(totalPages - 1, 0);
        List<String> links = new ArrayList<>();
        if (page + 1 < totalPages) {
            links.add(link(currentRequest, page + 1, size, "next"));
        }
        if (page > 0) {
            links.add(link(currentRequest, page - 1, size, "prev"));
        }
        links.add(link(currentRequest, lastPage, size, "last"));
        links.add(link(currentRequest, 0, size, "first"));

        HttpHeaders headers = new HttpHeaders();
        headers.add(TOTAL_COUNT, Long.toString(totalElements));
        headers.add(HttpHeaders.LINK, String.join(",", links));
        return headers;
    }
}
