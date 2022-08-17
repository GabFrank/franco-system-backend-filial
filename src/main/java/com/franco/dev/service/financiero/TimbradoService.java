package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.repository.financiero.TimbradoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class TimbradoService extends CrudService<Timbrado, TimbradoRepository> {

    private final TimbradoRepository repository;

    @Override
    public TimbradoRepository getRepository() {
        return repository;
    }

    @Override
    public Timbrado save(Timbrado entity) {
        Timbrado e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}