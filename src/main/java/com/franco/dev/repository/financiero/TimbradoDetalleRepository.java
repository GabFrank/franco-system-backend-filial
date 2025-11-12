package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface TimbradoDetalleRepository extends HelperRepository<TimbradoDetalle, Long> {

    default Class<TimbradoDetalle> getEntityClass() {
        return TimbradoDetalle.class;
    }

    List<TimbradoDetalle> findByTimbradoId(Long id);

    TimbradoDetalle findFirstByPuntoDeVentaIdOrderByIdDesc(Long id);

    TimbradoDetalle findFirstByPuntoDeVentaIdAndActivoTrueAndTimbrado_ActivoTrueAndTimbrado_IsElectronicoOrderByIdDesc(
            Long puntoDeVentaId, Boolean isElectronico);

}