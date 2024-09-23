package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.repository.financiero.MaletinRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MaletinService extends CrudService<Maletin, MaletinRepository> {

    private final MaletinRepository repository;

    @Autowired
    private SucursalService sucursalService;

    @Override
    public MaletinRepository getRepository() {
        return repository;
    }

//    public List<Maletin> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public Maletin findByDescripcion(String texto){
        return repository.findByDescripcionIgnoreCase(texto);
    }

    public List<Maletin> searchByAll(String texto){
        texto = texto.toUpperCase();
        return repository.findByAll(texto);
    }

    @Override
    public Maletin save(Maletin entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getCreadoEn()==null) entity.setCreadoEn(LocalDateTime.now()   );
        if(entity.getSucursal()!=null) {
            if(entity.getSucursal().getId() == 0){
                entity.setSucursal(sucursalService.findById(env.getProperty("sucursalId", Long.class)).orElse(null));
            } else {
                entity.setSucursal(sucursalService.findById(entity.getSucursal().getId()).orElse(null));
            }
        }
        Maletin e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public Maletin abrirMaletin(Long id){
        Maletin m = findById(id).orElse(null);
        if(m!=null){
            m.setAbierto(true);
            return save(m);
        }
        return null;
    }

    public Maletin cerrarMaletin(Long id){
        Maletin m = findById(id).orElse(null);
        if(m!=null){
            m.setAbierto(false);
            return save(m);
        }
        return null;
    }
}