package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoPersonasRepository extends HelperRepository<MovimientoPersonas, Long> {

    default Class<MovimientoPersonas> getEntityClass() {
        return MovimientoPersonas.class;
    }

    public List<MovimientoPersonas> findAllByPersonaIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByIdAsc(Long id, LocalDateTime start, LocalDateTime end);

    public List<MovimientoPersonas> findAllByPersonaIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualAndActivoOrderByIdAsc(Long id, LocalDateTime start, LocalDateTime end, Boolean activo);
}