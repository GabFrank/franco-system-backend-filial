package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.productos.PresentacionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.utils.ImageService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PresentacionService extends CrudService<Presentacion, PresentacionRepository, Long> {

    private final PresentacionRepository repository;

    @Autowired
    private ImageService imageService;

    @Override
    public PresentacionRepository getRepository() {
        return repository;
    }
    public List<Presentacion> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase())
                .stream()
                .sorted(Comparator.comparing(Presentacion::getCantidad))
                .collect(Collectors.toList());
    }
    public List<Presentacion> findByProductoIdOrderByCantidadAsc(Long id){
        return repository.findByProductoIdOrderByCantidadAsc(id);
    }
    public List<Presentacion> findByProductoId(Long id){
        return findByProductoIdOrderByCantidadAsc(id);
    }

    @Override
    public Presentacion save(Presentacion entity){
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        Presentacion e = super.save(entity);
//        personaPublisher.publish(p);
        repository.flush();
        return e;
    }

    public Presentacion findByPrincipalAndProductoId(Boolean principal, Long id){
        return repository.findByPrincipalAndProductoId(principal, id);
    }

    public void enviarImagenes(Long sucId){
        List<Presentacion> presentacionList = findAll2();
        for(Presentacion p: presentacionList){
            String image = imageService.getImageWithMediaType(p.getId()+".jpg", imageService.getImagePresentaciones());
            if(image!="") {
                log.info("Imagen encontrada: " + imageService.getImagePresentaciones()+p.getId()+".jpg");
//                propagacionService.propagarImagen(image, p.getId() + ".jpg", TipoEntidad.PRESENTACION, sucId);
            } else {
//                log.info("Imagen no encontrada: " + imageService.getImagePresentaciones()+p.getId()+".jpg");
            }
        }
    }
}