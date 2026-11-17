package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reservation_processes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationProcessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reservationProcessId;

    private String holdId;

    // Por valor, sin clave foránea: el usuario y el profesional viven en otros servicios
    private String userId;

    private Long professionalId;

    private String professionalFirstName;

    private String professionalLastName;

    private LocalDate appointmentDate;

    private LocalTime startTime;

    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private Instant expiresAt;

    private String patientFirstName;

    private String patientLastName;

    private String patientPhone;

    private String requestEventId;

    private Long reservationId;

    private String failureReason;

    private String cancellationReason;

    private Instant cancelledAt;

    private Instant createdAt;

    private Instant updatedAt;

    // Bloqueo optimista: Hibernate lo compara al guardar y lo incrementa en cada cambio
    @Version
    private Long version;
}
