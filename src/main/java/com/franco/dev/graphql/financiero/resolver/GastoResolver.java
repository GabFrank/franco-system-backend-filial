package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.GastoDetalle;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.service.financiero.GastoDetalleService;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.TipoGastoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GastoResolver implements GraphQLResolver<Gasto> {

    private final Logger log = LoggerFactory.getLogger(GastoResolver.class);

    @Autowired
    private GastoDetalleService gastoDetalleService;

    public List<GastoDetalle> gastoDetalleList(Gasto e){
        return gastoDetalleService.findByGastoId(e.getId());
    }

    @Autowired
    private MonedaService monedaService;

    public Double valorGs(Gasto e){
        List<GastoDetalle> gastoDetalleList = gastoDetalleService.findByGastoId(e.getId());
        Double valor = new Double(0);
        for (GastoDetalle g : gastoDetalleList){
            log.warn("gasto detalle: "+ g.getMoneda().getDenominacion());
            if(g.getMoneda().getDenominacion().contains("GUARANI")){
                log.warn(g.getMoneda().getDenominacion());
                valor = valor + g.getCantidad();
            }
        }
        return valor;
    }

    public Double valorRs(Gasto e){
        List<GastoDetalle> gastoDetalleList = gastoDetalleService.findByGastoId(e.getId());
        Double valor = new Double(0);
        for (GastoDetalle g : gastoDetalleList){
            if(g.getMoneda().getDenominacion().contains("REAL")){
                valor += g.getCantidad();
            }
        }
        return valor;
    }

    public Double valorDs(Gasto e){
        List<GastoDetalle> gastoDetalleList = gastoDetalleService.findByGastoId(e.getId());
        Double valor = new Double(0);
        for (GastoDetalle g : gastoDetalleList){
            if(g.getCantidad() != 0.0){
                Moneda moneda = monedaService.findById(g.getMoneda().getId()).orElse(null);
                if(moneda!=null ){
                    if(g.getMoneda().getDenominacion().contains("DOLAR")){
                        valor += g.getCantidad();
                }
                }
            }
        }
        return valor;
    }

}
