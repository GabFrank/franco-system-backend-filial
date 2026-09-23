package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfiguracionFacturacionRepository extends HelperRepository<ConfiguracionFacturacion, Long> {

    default Class<ConfiguracionFacturacion> getEntityClass() {
        return ConfiguracionFacturacion.class;
    }

    /**
     * La tabla tiene una fila global y a lo sumo una por sucursal, asi que se trae entera y se
     * elige en Java. El orden desempata filas duplicadas (el espejo no tiene UNIQUE): gana la
     * modificada mas recientemente.
     */
    List<ConfiguracionFacturacion> findAllByOrderByModificadoEnDescIdDesc();
}
