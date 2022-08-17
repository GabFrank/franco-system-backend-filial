package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface TimbradoDetalleRepository extends HelperRepository<TimbradoDetalle, Long> {

    default Class<TimbradoDetalle> getEntityClass() {
        return TimbradoDetalle.class;
    }

    public List<TimbradoDetalle> findByTimbradoId(Long id);

    public TimbradoDetalle findFirstByPuntoDeVentaIdOrderByIdDesc(Long id);

}