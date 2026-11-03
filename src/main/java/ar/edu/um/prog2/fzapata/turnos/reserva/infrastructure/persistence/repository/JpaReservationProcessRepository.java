package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity.ReservationProcessEntity;

@Repository
public interface JpaReservationProcessRepository extends JpaRepository<ReservationProcessEntity, Long> {

    Optional<ReservationProcessEntity> findByIdAndUserId(Long id, String userId);

    Optional<ReservationProcessEntity> findByReservationProcessId(String reservationProcessId);

    @Query("""
            select r from ReservationProcessEntity r
            where r.userId = :userId
              and (:status is null or r.status = :status)
              and (:hideDiscarded = false or r.status not in :discarded)""")
    Page<ReservationProcessEntity> search(@Param("userId") String userId, @Param("status") ReservationStatus status,
            @Param("hideDiscarded") boolean hideDiscarded, @Param("discarded") Collection<ReservationStatus> discarded,
            Pageable pageable);
}
