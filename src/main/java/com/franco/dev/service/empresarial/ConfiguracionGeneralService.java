package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ConfiguracionGeneralService extends CrudService<ConfiguracionGeneral, ConfiguracionGeneralRepository> {

    private final ConfiguracionGeneralRepository repository;

    @Override
    public ConfiguracionGeneralRepository getRepository() {
        return repository;
    }

//    @Override
//    public ConfiguracionGeneral save(ConfiguracionGeneral entity) {
//        ConfiguracionGeneral e = super.save(entity);
////        personaPublisher.publish(p);
//        return e;
//    }
}