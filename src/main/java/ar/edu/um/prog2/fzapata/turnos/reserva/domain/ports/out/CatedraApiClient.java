package ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentPage;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.AppointmentQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Cancellation;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Hold;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.HoldConfirmation;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.Occupancy;

// Operaciones REST sobre el servicio central de la cátedra, con la cuenta técnica del proyecto. Un rechazo funcional
// de la cátedra sale como CatedraRejectedException; una caída, como CatedraUnavailableException (o CatedraNotReachedException
// si se sabe que el pedido nunca llegó)
public interface CatedraApiClient {

    // Horarios no disponibles del profesional entre dos fechas (inclusivas). Una lista vacía es válida
    List<Occupancy> findOccupancies(Long professionalId, LocalDate from, LocalDate to);

    // La cátedra no tiene idempotencia ni forma de recuperar los ids de un hold: si el resultado queda desconocido
    // (timeout o 5xx) este método NO repite el pedido, y quien lo llama debe reconciliar con las ocupaciones
    Hold createHold(Long professionalId, LocalDate date, LocalTime startTime, String externalPatientId);

    // Repetirlo es seguro: la cátedra responde RESERVATION_PROCESS_ALREADY_CONFIRMED si ya estaba confirmado
    HoldConfirmation confirmHold(String holdId, String externalPatientId, String patientFirstName,
            String patientLastName);

    // La cátedra devuelve las reservas de toda la cuenta técnica: hay que filtrar por externalPatientId
    AppointmentPage findAppointments(AppointmentQuery query, int page, int size);

    // reason es opcional (hasta 500 caracteres). Idempotente: cancelar una reserva ya cancelada devuelve su estado
    Cancellation cancelAppointment(String reservationProcessId, String reason);
}
