package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.PuntoDeVenta;
import com.franco.dev.repository.empresarial.PuntoDeVentaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PuntoDeVentaService extends CrudService<PuntoDeVenta, PuntoDeVentaRepository> {

    private final PuntoDeVentaRepository repository;

    @Override
    public PuntoDeVentaRepository getRepository() {
        return repository;
    }

    public List<PuntoDeVenta> findBySucursalId(Long id) {
        return repository.findBySucursalId(id);
    }

    @Override
    public PuntoDeVenta save(PuntoDeVenta entity) {
        PuntoDeVenta e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public PuntoDeVenta getPuntoDeVentaActual(Long id) {
        return repository.findById(id).orElse(null);
    }

}