package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.repository.financiero.LoteDERepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class LoteDEService extends CrudService<LoteDE, LoteDERepository> {

    private final LoteDERepository repository;

    @Override
    public LoteDERepository getRepository() {
        return repository;
    }
    
    public LoteDE findByProtocolo(String protocolo) {
        return repository.findByProtocolo(protocolo);
    }
    
    public List<LoteDE> findByEstado(EstadoLoteDE estado) {
        return repository.findByEstadoOrderByCreadoEnAsc(estado);
    }
    
    public List<LoteDE> findByEstados(List<EstadoLoteDE> estados) {
        return repository.findByEstadoInOrderByCreadoEnAsc(estados);
    }
}
