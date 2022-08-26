package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaCreditoRepository extends HelperRepository<VentaCredito, Long> {

    default Class<VentaCredito> getEntityClass() {
        return VentaCredito.class;
    }

    public List<VentaCredito> findAllByClienteIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByIdAsc(Long id, LocalDateTime start, LocalDateTime end);

//    Moneda findByPaisId(Long id);

}