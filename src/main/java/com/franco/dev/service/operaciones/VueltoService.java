package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Vuelto;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.VueltoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class VueltoService extends CrudService<Vuelto, VueltoRepository> {
    private final VueltoRepository repository;

    @Override
    public VueltoRepository getRepository() {
        return repository;
    }

//    public List<Vuelto> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//    }

    @Override
    public Vuelto save(Vuelto entity) {
        Vuelto e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public Vuelto saveAndSend(Vuelto entity, Boolean recibir) {
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        Vuelto e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.VUELTO, recibir);
        return e;
    }
}