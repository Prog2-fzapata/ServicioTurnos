package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxEvent;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.OutboxStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.OutboxRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.mapper.OutboxEventMapper;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.repository.JpaOutboxEventRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaOutboxRepositoryAdapter implements OutboxRepository {

    private final JpaOutboxEventRepository jpaOutboxEventRepository;
    private final OutboxEventMapper outboxEventMapper;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return outboxEventMapper.toDomainModel(jpaOutboxEventRepository.save(outboxEventMapper.toEntity(event)));
    }

    @Override
    public Optional<OutboxEvent> findById(Long id) {
        return jpaOutboxEventRepository.findById(id).map(outboxEventMapper::toDomainModel);
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return jpaOutboxEventRepository
                .findByStatusOrderByCreatedAtAscIdAsc(OutboxStatus.PENDING, PageRequest.of(0, limit)).stream()
                .map(outboxEventMapper::toDomainModel).toList();
    }
}
