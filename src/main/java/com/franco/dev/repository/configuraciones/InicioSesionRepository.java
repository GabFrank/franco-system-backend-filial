package com.franco.dev.repository.configuraciones;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InicioSesionRepository extends HelperRepository<InicioSesion, Long> {

    default Class<InicioSesion> getEntityClass() {
        return InicioSesion.class;
    }

    public Page<InicioSesion> findByUsuarioIdAndHoraFinIsNullOrderByIdDesc(Long id, Pageable page);
    public Page<InicioSesion> findByUsuarioIdAndSucursalIdAndHoraFinIsNullOrderByIdDesc(Long id, Long sucId, Pageable page);
}