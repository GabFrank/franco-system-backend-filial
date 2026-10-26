package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionCobro;
import com.franco.dev.repository.financiero.ConfiguracionCobroRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ConfiguracionCobroService extends CrudService<ConfiguracionCobro, ConfiguracionCobroRepository> {

    private final ConfiguracionCobroRepository repository;

    @Override
    public ConfiguracionCobroRepository getRepository() {
        return repository;
    }

    /**
     * La configuracion replicada desde el central. Si todavia no llego ninguna fila (filial recien
     * migrado, o nadie la guardo en el central), un default con tope del 20% SIN persistir: el
     * filial nunca escribe esta tabla.
     */
    public ConfiguracionCobro findOrDefault() {
        List<ConfiguracionCobro> list = repository.findAllByOrderByIdAsc();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        ConfiguracionCobro def = new ConfiguracionCobro();
        def.setId(0L);
        return def;
    }
}
