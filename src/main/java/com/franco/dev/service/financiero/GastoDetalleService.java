package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.GastoDetalle;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.GastoDetalleRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class GastoDetalleService extends CrudService<GastoDetalle, GastoDetalleRepository> {

    private final GastoDetalleRepository repository;

    @Override
    public GastoDetalleRepository getRepository() {
        return repository;
    }

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private GastoService gastoService;

//    public List<GastoDetalle> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<GastoDetalle> findByGastoId(Long id){
        return repository.findByGastoId(id);
    }

    @Override
    public GastoDetalle save(GastoDetalle entity) throws GraphQLException{
        Double totalEnCaja = movimientoCajaService.totalEnCajaPorCajaIdAndMonedaId(entity.getGasto().getCaja().getId(), entity.getMoneda().getId());
        if(totalEnCaja < entity.getCantidad()){
            gastoService.deleteById(entity.getGasto().getId());
            throw new GraphQLException("No hay suficiente valor en caja");
        } else {
            GastoDetalle e = super.save(entity);
            MovimientoCaja movimientoCaja = new MovimientoCaja();
            movimientoCaja.setTipoMovimiento(PdvCajaTipoMovimiento.GASTO);
            movimientoCaja.setCantidad(e.getCantidad() * -1);
            movimientoCaja.setUsuario(e.getUsuario());
            movimientoCaja.setMoneda(e.getMoneda());
            movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
            movimientoCaja.setPdvCaja(entity.getGasto().getCaja());
            movimientoCaja.setReferencia(e.getId());
            movimientoCaja.setCreadoEn(e.getCreadoEn());
            movimientoCajaService.save(movimientoCaja);
            return e;
        }
//        personaPublisher.publish(p);
    }
}