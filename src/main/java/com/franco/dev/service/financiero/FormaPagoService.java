package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.repository.financiero.CambioRepository;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class FormaPagoService extends CrudService<FormaPago, FormaPagoRepository> {

    private final FormaPagoRepository repository;

    @Override
    public FormaPagoRepository getRepository() {
        return repository;
    }

    @Override
    public FormaPago save(FormaPago entity) {
        FormaPago e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}