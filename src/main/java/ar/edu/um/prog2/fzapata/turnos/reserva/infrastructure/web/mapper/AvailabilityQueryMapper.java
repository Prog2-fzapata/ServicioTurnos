package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.web.mapper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.InvalidDateRangeException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AvailabilityQuery;
import ar.edu.um.prog2.fzapata.turnos.shared.config.AvailabilityProperties;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AvailabilityQueryMapper {

    private final AvailabilityProperties availabilityProperties;

    // Valida el rango antes de llamar a nadie: un rango inválido no justifica consultar catálogo ni la cátedra
    public AvailabilityQuery toQuery(Long professionalId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidDateRangeException("El parámetro to no puede ser anterior a from");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > availabilityProperties.maxRangeDays()) {
            throw new InvalidDateRangeException(
                    "El rango no puede superar los " + availabilityProperties.maxRangeDays() + " días");
        }
        return new AvailabilityQuery(professionalId, from, to);
    }
}
