package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Documento;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.DocumentoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class DocumentoService extends CrudService<Documento, DocumentoRepository> {

    private final DocumentoRepository repository;

    @Override
    public DocumentoRepository getRepository() {
        return repository;
    }

//    public List<Documento> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<Documento> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto);
    }

    @Override
    public Documento save(Documento entity) {
        Documento e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}