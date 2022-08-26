package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface VentaCreditoCuotaRepository extends HelperRepository<VentaCreditoCuota, Long> {

    default Class<VentaCreditoCuota> getEntityClass() {
        return VentaCreditoCuota.class;
    }

    public List<VentaCreditoCuota> findAllByVentaCreditoId(Long id);

//    Moneda findByPaisId(Long id);

}