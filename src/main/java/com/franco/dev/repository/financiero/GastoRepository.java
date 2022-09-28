package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GastoRepository extends HelperRepository<Gasto, Long> {

    default Class<Gasto> getEntityClass() {
        return Gasto.class;
    }

    public List<Gasto> findByCreadoEnBetween(LocalDateTime inicio, LocalDateTime fin);

    public List<Gasto> findByCajaId(Long id);

}