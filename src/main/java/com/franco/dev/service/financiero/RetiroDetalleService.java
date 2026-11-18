package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.repository.financiero.RetiroDetalleRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class RetiroDetalleService extends CrudService<RetiroDetalle, RetiroDetalleRepository> {

    private final RetiroDetalleRepository repository;

    @Override
    public RetiroDetalleRepository getRepository() {
        return repository;
    }

//    public List<RetiroDetalle> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<RetiroDetalle> findByRetiroId(Long id) {
        return repository.findByRetiroId(id);
    }

    public Double findByRetiroIdAndMonedaId(Long id, Long monedaId) {
        List<RetiroDetalle> retiroDetalles = repository.findByRetiroIdAndMonedaId(id, monedaId);
        Double total = 0.0;
        for (RetiroDetalle r : retiroDetalles) {
            total += r.getCantidad();
        }
        return total;
    }

    public List<RetiroDetalle> findByCajId(Long id) {
        return repository.findByRetiroCajaSalidaId(id);
    }

    @Override
    public RetiroDetalle saveAndSend(RetiroDetalle entity, Boolean recibir) throws GraphQLException {
        entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        // Ya no se escribe movimiento_caja: el saldo sale de generarBalance (#80).
        return super.save(entity);
    }
}