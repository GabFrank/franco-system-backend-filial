package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.repository.financiero.ConfiguracionVentaTarjetaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ConfiguracionVentaTarjetaService extends CrudService<ConfiguracionVentaTarjeta, ConfiguracionVentaTarjetaRepository> {

    private final ConfiguracionVentaTarjetaRepository repository;

    @Override
    public ConfiguracionVentaTarjetaRepository getRepository() {
        return repository;
    }

    /**
     * Devuelve la configuracion replicada desde el central. Si todavia no llego
     * ninguna fila (filial recien migrado, REFRESH PUBLICATION pendiente),
     * devuelve un default transitorio deshabilitado SIN persistir: el filial
     * nunca escribe esta tabla (es read-only, direction MAIN_TO_ALL).
     */
    public ConfiguracionVentaTarjeta findOrDefault() {
        List<ConfiguracionVentaTarjeta> list = repository.findAllByOrderByIdAsc();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        ConfiguracionVentaTarjeta def = new ConfiguracionVentaTarjeta();
        def.setId(0L);
        def.setHabilitado(false);
        return def;
    }
}
