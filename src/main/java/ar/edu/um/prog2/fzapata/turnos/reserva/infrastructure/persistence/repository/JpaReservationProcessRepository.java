package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity.ReservationProcessEntity;

@Repository
public interface JpaReservationProcessRepository extends JpaRepository<ReservationProcessEntity, Long> {
}
