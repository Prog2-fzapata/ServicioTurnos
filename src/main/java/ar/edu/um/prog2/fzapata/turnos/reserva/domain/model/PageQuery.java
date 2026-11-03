package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

// Página pedida: la primera es la 0
public record PageQuery(int page, int size) {

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        if (size < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser al menos 1");
        }
    }
}
