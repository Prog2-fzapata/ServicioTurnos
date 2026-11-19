package ar.edu.um.prog2.fzapata.turnos.reserva.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un profesional con su agenda vigente, tal como lo informa catálogo. Incluye lo deshabilitado:
// qué hacer con eso lo decide quien lo usa
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProfessional {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private boolean categoryEnabled;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private List<CatalogSchedule> weeklySchedules;

    // Los horarios de su agenda entre dos fechas, ambas inclusivas, ordenados por inicio. Es un mapa nuevo en cada
    // llamada: quien lo recibe puede quitarle horarios. Disponibilidad y reserva leen la agenda por acá, así que
    // aplican exactamente la misma regla
    public SortedMap<LocalDateTime, Slot> slotsBetween(LocalDate from, LocalDate to) {
        SortedMap<LocalDateTime, Slot> slots = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            addSlotsOn(slots, date);
        }
        return slots;
    }

    // Retorna el horario de su agenda que empieza exactamente a esa hora, si lo hay.
    // Con dos horarios que repiten el inicio, el del primer horario semanal, igual que en slotsBetween
    public Optional<Slot> slotAt(LocalDateTime start) {
        return weeklySchedules.stream() // Flujo de WeeklySchedules
                // Flujo de slots del día start
                .flatMap(schedule -> schedule.slotsOn(start.toLocalDate()).stream())
                // Se queda con los que empiezan en la misma hora
                .filter(slot -> slot.getStartTime().equals(start.toLocalTime()))
                // Retorna solo el primero
                .findFirst();
    }

    private void addSlotsOn(Map<LocalDateTime, Slot> slots, LocalDate date) {
        for (CatalogSchedule schedule : weeklySchedules) {
            for (Slot slot : schedule.slotsOn(date)) {
                // Dos horarios que se superponen pueden repetir un inicio: queda el primero
                slots.putIfAbsent(LocalDateTime.of(date, slot.getStartTime()), slot);
            }
        }
    }
}
