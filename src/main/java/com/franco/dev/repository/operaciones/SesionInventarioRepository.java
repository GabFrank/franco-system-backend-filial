package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.SesionInventario;
import com.franco.dev.repository.HelperRepository;

public interface SesionInventarioRepository extends HelperRepository<SesionInventario, Long> {

    default Class<SesionInventario> getEntityClass() {
        return SesionInventario.class;
    }

}