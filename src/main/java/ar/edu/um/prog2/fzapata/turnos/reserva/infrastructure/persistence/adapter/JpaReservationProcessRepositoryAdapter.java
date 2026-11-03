package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PageQuery;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.PagedResult;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationFilter;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationStatus;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.entity.ReservationProcessEntity;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.mapper.ReservationProcessMapper;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.repository.JpaReservationProcessRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaReservationProcessRepositoryAdapter implements ReservationProcessRepository {

    // El id desempata los turnos a la misma hora, para que las páginas no repitan ni salteen filas
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "appointmentDate", "startTime", "id");

    private final JpaReservationProcessRepository jpaReservationProcessRepository;
    private final ReservationProcessMapper reservationProcessMapper;

    @Override
    public Optional<ReservationProcess> findById(Long id) {
        return jpaReservationProcessRepository.findById(id).map(reservationProcessMapper::toDomainModel);
    }

    @Override
    public Optional<ReservationProcess> findByReservationProcessId(String reservationProcessId) {
        return jpaReservationProcessRepository.findByReservationProcessId(reservationProcessId)
                .map(reservationProcessMapper::toDomainModel);
    }

    @Override
    public Optional<ReservationProcess> findByIdAndUserId(Long id, String userId) {
        return jpaReservationProcessRepository.findByIdAndUserId(id, userId)
                .map(reservationProcessMapper::toDomainModel);
    }

    @Override
    public PagedResult<ReservationProcess> search(ReservationFilter filter, PageQuery page) {
        Page<ReservationProcessEntity> found = jpaReservationProcessRepository.search(filter.userId(),
                filter.status(), filter.hideDiscarded(), ReservationStatus.DISCARDED,
                PageRequest.of(page.page(), page.size(), NEWEST_FIRST));
        return new PagedResult<>(found.getContent().stream().map(reservationProcessMapper::toDomainModel).toList(),
                found.getTotalElements(), page);
    }

    // La versión viaja en el modelo: si en la base ya es otra, JPA lanza el error de bloqueo optimista y acá se
    // traduce a la excepción de la aplicación. También dentro de una transacción abierta: guardar un proceso leído
    // antes con una versión que ya cambió falla en esta llamada
    @Override
    public ReservationProcess save(ReservationProcess process) {
        try {
            return reservationProcessMapper
                    .toDomainModel(jpaReservationProcessRepository.save(reservationProcessMapper.toEntity(process)));
        } catch (OptimisticLockingFailureException e) {
            throw new ReservationConflictException(process.getId(), e);
        }
    }
}
