package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.repository.financiero.EventoNominacionDERepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class EventoNominacionDEService {

    private final EventoNominacionDERepository repository;

    public EventoNominacionDEService(EventoNominacionDERepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EventoNominacionDE save(EventoNominacionDE evento) {
        return repository.save(evento);
    }

    public Optional<EventoNominacionDE> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<EventoNominacionDE> findByEventoId(String eventoId) {
        return repository.findByEventoId(eventoId);
    }

    public List<EventoNominacionDE> findByDocumentoElectronicoId(Long documentoId) {
        return repository.findByDocumentoElectronicoId(documentoId);
    }

    public boolean tieneNominacionAprobada(Long documentoId) {
        return repository.existeNominacionAprobada(documentoId);
    }
    
    public List<EventoNominacionDE> findActivosByDocumentoElectronicoId(Long documentoId) {
        return repository.findActivosByDocumentoElectronicoId(documentoId);
    }
    
    public List<EventoNominacionDE> findActivosByCdcDocumento(String cdcDocumento) {
        return repository.findActivosByCdcDocumento(cdcDocumento);
    }
}

