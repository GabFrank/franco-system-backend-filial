package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.RetiroDetalleRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class RetiroDetalleService extends CrudService<RetiroDetalle, RetiroDetalleRepository> {

    private final RetiroDetalleRepository repository;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private RetiroService retiroService;

    @Override
    public RetiroDetalleRepository getRepository() {
        return repository;
    }

    @Autowired
    public CambioService cambioService;

//    public List<RetiroDetalle> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<RetiroDetalle> findByRetiroId(Long id){
        return repository.findByRetiroId(id);
    }

    public Double findByRetiroIdAndMonedaId(Long id, Long monedaId){
        List<RetiroDetalle> retiroDetalles = repository.findByRetiroIdAndMonedaId(id, monedaId);
        Double total = 0.0;
        for(RetiroDetalle r:retiroDetalles){
            total += r.getCantidad();
        }
        return total;
    }

    public List<RetiroDetalle> findByCajId(Long id){
        return repository.findByRetiroCajaSalidaId(id);
    }

    @Override
    public RetiroDetalle save(RetiroDetalle entity) throws GraphQLException{
        Long cajaId = entity.getRetiro().getCajaSalida().getId();
        Double total = entity.getCantidad();
        Double totalEnCaja = movimientoCajaService.totalEnCajaPorCajaIdAndMonedaId(cajaId, entity.getMoneda().getId());
        if(total > totalEnCaja){
            retiroService.deleteById(entity.getRetiro().getId());
            throw new GraphQLException("El valor de retiro es mayor al total en caja");
        } else {
            RetiroDetalle e = super.save(entity);
            MovimientoCaja movimientoCaja = new MovimientoCaja();
            movimientoCaja.setTipoMovimiento(PdvCajaTipoMovimiento.RETIRO);
            movimientoCaja.setReferencia(e.getId());
            movimientoCaja.setPdvCaja(entity.getRetiro().getCajaSalida());
            movimientoCaja.setCantidad(entity.getCantidad() * -1);
            movimientoCaja.setCambio(cambioService.findLastByMonedaId(entity.getMoneda().getId()));
            movimientoCaja.setMoneda(entity.getMoneda());
            movimientoCaja.setUsuario(entity.getUsuario());
            movimientoCajaService.save(movimientoCaja);
//        personaPublisher.publish(p);
                    return e;
        }
    }

    @Override
    public RetiroDetalle saveAndSend(RetiroDetalle entity, Boolean recibir) throws GraphQLException{
        Long cajaId = entity.getRetiro().getCajaSalida().getId();
        Double total = entity.getCantidad();
        Double totalEnCaja = movimientoCajaService.totalEnCajaPorCajaIdAndMonedaId(cajaId, entity.getMoneda().getId());
        if(total > totalEnCaja){
            retiroService.deleteById(entity.getRetiro().getId());
            throw new GraphQLException("El valor de retiro es mayor al total en caja");
        } else {
            entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
            RetiroDetalle e = super.save(entity);
            propagacionService.propagarEntidad(e, TipoEntidad.RETIRO_DETALLE, recibir);
            MovimientoCaja movimientoCaja = new MovimientoCaja();
            movimientoCaja.setTipoMovimiento(PdvCajaTipoMovimiento.RETIRO);
            movimientoCaja.setReferencia(e.getId());
            movimientoCaja.setPdvCaja(entity.getRetiro().getCajaSalida());
            movimientoCaja.setCantidad(entity.getCantidad() * -1);
            movimientoCaja.setCambio(cambioService.findLastByMonedaId(entity.getMoneda().getId()));
            movimientoCaja.setMoneda(entity.getMoneda());
            movimientoCaja.setUsuario(entity.getUsuario());
            movimientoCajaService.saveAndSend(movimientoCaja, false);
//        personaPublisher.publish(p);
            return e;
        }
    }
}