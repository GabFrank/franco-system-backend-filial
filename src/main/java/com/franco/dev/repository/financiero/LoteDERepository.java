package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface LoteDERepository extends HelperRepository<LoteDE, Long> {
    
    List<LoteDE> findByEstadoOrderByCreadoEnAsc(EstadoLoteDE estado);
    
    List<LoteDE> findByEstadoOrderByFechaUltimoIntentoAsc(EstadoLoteDE estado);
    
    List<LoteDE> findByEstadoInOrderByCreadoEnAsc(List<EstadoLoteDE> estados);
    
    LoteDE findByProtocolo(String protocolo);
}
