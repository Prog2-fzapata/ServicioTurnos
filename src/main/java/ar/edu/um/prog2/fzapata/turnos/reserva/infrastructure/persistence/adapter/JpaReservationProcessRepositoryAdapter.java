package ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.reserva.application.exception.ReservationConflictException;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.model.ReservationProcess;
import ar.edu.um.prog2.fzapata.turnos.reserva.domain.ports.out.ReservationProcessRepository;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.mapper.ReservationProcessMapper;
import ar.edu.um.prog2.fzapata.turnos.reserva.infrastructure.persistence.repository.JpaReservationProcessRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaReservationProcessRepositoryAdapter implements ReservationProcessRepository {

    private final JpaReservationProcessRepository jpaReservationProcessRepository;
    private final ReservationProcessMapper reservationProcessMapper;

    @Override
    public Optional<ReservationProcess> findById(Long id) {
        return jpaReservationProcessRepository.findById(id).map(reservationProcessMapper::toDomainModel);
    }

    // La versión viaja en el modelo: si en la base ya es otra, JPA lanza el error de bloqueo optimista y acá se
    // traduce a la excepción de la aplicación
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
