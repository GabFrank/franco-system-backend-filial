package com.franco.dev.repository.configuraciones;

import com.franco.dev.domain.configuracion.Actualizacion;
import com.franco.dev.domain.configuracion.enums.TipoActualizacion;
import com.franco.dev.repository.HelperRepository;

public interface ActualizacionRepository extends HelperRepository<Actualizacion, Long> {

    default Class<Actualizacion> getEntityClass() {
        return Actualizacion.class;
    }

    public Actualizacion findFirstByTipoOrderByIdDesc(TipoActualizacion tipo);
    public Actualizacion findFirstByOrderByIdDesc();

}