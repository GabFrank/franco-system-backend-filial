package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.graphql.operaciones.publisher.DeliveryPublisher;
import com.franco.dev.graphql.personas.publisher.PersonaPublisher;
import com.franco.dev.repository.operaciones.DeliveryRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class DeliveryService extends CrudService<Delivery, DeliveryRepository> {
    private final DeliveryRepository repository;
    private final DeliveryPublisher deliveryPublisher;


    @Override
    public DeliveryRepository getRepository() {
        return repository;
    }

    public List<Delivery> findByEstado(DeliveryEstado estado){
        return  repository.findByEstado(estado);
    }

    public List<Delivery> findByEstadoNotIn(DeliveryEstado estado){
        return  repository.findActivos();
    }

    public List<Delivery> findTop10(){
        return repository.findUltimos10();
    }

    @Override
    public Delivery save(Delivery entity) {
        Delivery e = super.save(entity);
        deliveryPublisher.publish(e);
        return e;
    }
}