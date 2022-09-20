package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class TimbradoDetalleService extends CrudService<TimbradoDetalle, TimbradoDetalleRepository> {

    private final TimbradoDetalleRepository repository;

    @Override
    public TimbradoDetalleRepository getRepository() {
        return repository;
    }

    public List<TimbradoDetalle> findByTimbradoId(Long id) {
        return repository.findByTimbradoId(id);
    }

    public Long aumentarNumeroFactura(TimbradoDetalle timbradoDetalle){
        timbradoDetalle.setNumeroActual(timbradoDetalle.getNumeroActual() + 1);
        timbradoDetalle = save(timbradoDetalle);
        propagacionService.propagarEntidad(timbradoDetalle, TipoEntidad.TIMBRADO_DETALLE, false);
        return timbradoDetalle.getNumeroActual();
    }

    @Override
    public TimbradoDetalle save(TimbradoDetalle entity) {
        TimbradoDetalle e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public TimbradoDetalle getTimbradoDetalleActual(Long id) {
        return repository.findFirstByPuntoDeVentaIdOrderByIdDesc(id);
    }
}