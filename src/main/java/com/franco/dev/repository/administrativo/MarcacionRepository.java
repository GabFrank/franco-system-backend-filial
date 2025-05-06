package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.domain.configuracion.Actualizacion;
import com.franco.dev.domain.configuracion.enums.TipoActualizacion;
import com.franco.dev.repository.HelperRepository;

public interface MarcacionRepository extends HelperRepository<Marcacion, Long> {

    default Class<Marcacion> getEntityClass() {
        return Marcacion.class;
    }

}