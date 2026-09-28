package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.repository.configuraciones.InicioSesionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class InicioSesionService extends CrudService<InicioSesion, InicioSesionRepository> {

    private final InicioSesionRepository repository;

    @Override
    public InicioSesionRepository getRepository() {
        return repository;
    }

    public List<InicioSesion> findAll() {
        return repository.findAll();
    }

    @Override
    public InicioSesion save(InicioSesion entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        InicioSesion e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public Page<InicioSesion> findByUsuarioIdAndHoraFinIsNul(Long id, Long sucId, Pageable pageable){
        if(sucId!=null){
            return repository.findByUsuarioIdAndSucursalIdAndHoraFinIsNullOrderByIdDesc(id, sucId, pageable);
        } else {
            return repository.findByUsuarioIdAndHoraFinIsNullOrderByIdDesc(id, pageable);
        }
    }

    // Por (id, sucursal) y no por id: la PK real es compuesta y la entidad solo mapea id.
    public Optional<InicioSesion> findByIdAndSucursalId(Long id, Long sucursalId) {
        return repository.findByIdAndSucursalId(id, sucursalId);
    }

    @Override
    public InicioSesion saveAndSend(InicioSesion entity, Boolean recibir) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        InicioSesion e = super.save(entity);
//        propagacionService.propagarEntidad(e, TipoEntidad.INICIO_SESION, recibir);
//        personaPublisher.publish(p);
        return e;
    }
}