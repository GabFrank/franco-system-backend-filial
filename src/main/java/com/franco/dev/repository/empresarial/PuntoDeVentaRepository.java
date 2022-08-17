package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.PuntoDeVenta;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface PuntoDeVentaRepository extends HelperRepository<PuntoDeVenta, Long> {

    default Class<PuntoDeVenta> getEntityClass() {
        return PuntoDeVenta.class;
    }

    public List<PuntoDeVenta> findBySucursalId(Long id);

}