package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Los horarios libres de un profesional en un rango de fechas, ordenados por fecha y hora. Es una foto: el veredicto
// real lo da el hold al reservar
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Availability {

    private Long professionalId;
    private LocalDate from;
    private LocalDate to;
    private List<Slot> slots;
}
