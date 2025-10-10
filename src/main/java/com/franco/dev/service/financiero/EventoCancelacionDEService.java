package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.repository.financiero.EventoCancelacionDERepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class EventoCancelacionDEService {

    private final EventoCancelacionDERepository repository;

    public EventoCancelacionDEService(EventoCancelacionDERepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EventoCancelacionDE save(EventoCancelacionDE evento) {
        return repository.save(evento);
    }

    public Optional<EventoCancelacionDE> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<EventoCancelacionDE> findByCdcDocumento(String cdc) {
        return repository.findFirstByCdcDocumentoOrderByCreadoEnDesc(cdc);
    }

    public Optional<EventoCancelacionDE> findByEventoId(String eventoId) {
        return repository.findByEventoId(eventoId);
    }

    public List<EventoCancelacionDE> findByDocumentoElectronicoId(Long documentoId) {
        return repository.findByDocumentoElectronicoId(documentoId);
    }

    public boolean tieneCancelacionAprobada(Long documentoId) {
        return repository.existeCancelacionAprobada(documentoId);
    }
    
    public List<EventoCancelacionDE> findActivosByDocumentoElectronicoId(Long documentoId) {
        return repository.findActivosByDocumentoElectronicoId(documentoId);
    }
    
    public List<EventoCancelacionDE> findActivosByCdcDocumento(String cdcDocumento) {
        return repository.findActivosByCdcDocumento(cdcDocumento);
    }
    
    public List<EventoCancelacionDE> findEventosFallidos() {
        return repository.findEventosFallidos();
    }
    
    public Optional<EventoCancelacionDE> findUltimoEventoPorDocumento(Long documentoId) {
        List<EventoCancelacionDE> eventos = repository.findByDocumentoElectronicoIdOrderByCreadoEnDesc(documentoId);
        return eventos.isEmpty() ? Optional.empty() : Optional.of(eventos.get(0));
    }
}

