package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.graphql.operaciones.publisher.DeliveryPublisher;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.DeliveryRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@AllArgsConstructor
public class DeliveryService extends CrudService<Delivery, DeliveryRepository> {
    private final DeliveryRepository repository;
    private final DeliveryPublisher deliveryPublisher;


    @Override
    public DeliveryRepository getRepository() {
        return repository;
    }

    public List<Delivery> findByEstado(DeliveryEstado estado) {
        return repository.findByEstado(estado);
    }

    public List<Delivery> findByEstadoList(List<DeliveryEstado> estadoList, Long sucId) {
        Iterable<Delivery> deliveryIterable = repository.findByEstadoIn(estadoList);
        return StreamSupport.stream(deliveryIterable.spliterator(), false)
                .collect(Collectors.toList());
    }

    public List<Delivery> findByEstadoNotIn(DeliveryEstado estado) {
        return repository.findActivos();
    }

    public List<Delivery> findTop10() {
        return repository.findUltimos10();
    }

    public Delivery findByVentaId(Long id, Long sucId) {
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    public List<Delivery> findByVentaCajaId(Long id) {
        return repository.findByVentaCajaId(id);
    }

    public List<Delivery> findByVentaCajaIdAndEstadoIn(Long id, List<DeliveryEstado> estado){
        return repository.findByVentaCajaIdAndEstadoIn(id,estado);
    }

    @Override
    public Delivery save(Delivery entity) {
        Delivery e = super.save(entity);
        deliveryPublisher.publish(e);
        return e;
    }

    @Override
    public Delivery saveAndSend(Delivery entity, Boolean recibir) {
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        Delivery e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.DELIVERY, recibir);
        return e;
    }
}