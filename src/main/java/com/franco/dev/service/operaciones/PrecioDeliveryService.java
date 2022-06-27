package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.PrecioDelivery;
import com.franco.dev.repository.operaciones.PrecioDeliveryRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PrecioDeliveryService extends CrudService<PrecioDelivery, PrecioDeliveryRepository> {
    private final PrecioDeliveryRepository repository;

    @Override
    public PrecioDeliveryRepository getRepository() {
        return repository;
    }

//    public List<PrecioDelivery> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//    }

    @Override
    public PrecioDelivery save(PrecioDelivery entity) {
        PrecioDelivery e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}