package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un horario libre de la agenda. Las horas son locales de atención, sin zona horaria
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Slot {

    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
}
