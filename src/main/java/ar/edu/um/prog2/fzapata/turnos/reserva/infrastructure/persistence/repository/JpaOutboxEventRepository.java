package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity.OutboxEventEntity;

@Repository
public interface JpaOutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    // El id desempata los que se crearon en el mismo instante
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAscIdAsc(OutboxStatus status, Pageable pageable);
}
