package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ConteoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConteoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private ConteoMonedaGraphQL conteoMonedaGraphQL;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private MonedaService monedaService;

    @Autowired CambioService cambioService;

    private static final Logger log = LoggerFactory.getLogger(ConteoGraphQL.class);

    public Optional<Conteo> conteo(Long id) {return service.findById(id);}

    public List<Conteo> conteos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public Conteo saveConteo(ConteoInput input, List<ConteoMonedaInput> conteoMonedaInputList, Long cajaId, Boolean apertura){
        ModelMapper m = new ModelMapper();
        Conteo e = m.map(input, Conteo.class);
        Conteo conteo = null;
        PdvCaja pdvCaja = pdvCajaService.findById(cajaId).orElse(null);
        if(pdvCaja!=null){
            if(input.getUsuarioId()!=null){
                e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
            }
            conteo = service.save(e);
            if(conteo!=null){
                if(pdvCaja.getConteoApertura()==null){
                    log.warn("entranndo enn apertura");
                    pdvCaja.setConteoApertura(conteo);
                    pdvCaja.setFechaApertura(LocalDateTime.now());
                    pdvCajaService.save(pdvCaja);
                    List<Moneda> monedaList = monedaService.findAll2();
                    for(Moneda moneda: monedaList){
                        MovimientoCaja movimientoCaja = new MovimientoCaja();
                        if(moneda.getDenominacion().contains("GUARANI")) {
                            movimientoCaja.setMoneda(moneda);
                            movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                            movimientoCaja.setCantidad(input.getTotalGs());
                        } else if(moneda.getDenominacion().contains("REAL")) {
                            movimientoCaja.setMoneda(moneda);
                            movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                            movimientoCaja.setCantidad(input.getTotalRs());
                        } else if(moneda.getDenominacion().contains("DOLAR")) {
                            movimientoCaja.setMoneda(moneda);
                            movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                            movimientoCaja.setCantidad(input.getTotalDs());
                        }
                        if(movimientoCaja.getMoneda()!=null){
                            movimientoCaja.setPdvCaja(pdvCaja);
                            movimientoCaja.setReferencia(conteo.getId());
                            movimientoCaja.setTipoMovimiento(PdvCajaTipoMovimiento.CAJA_INICIAL);
                            movimientoCajaService.save(movimientoCaja);
                        }
                    }
                } else {
                    pdvCaja.setConteoCierre(conteo);
                    pdvCaja.setFechaCierre(LocalDateTime.now());
                    pdvCaja.setActivo(false);
                    pdvCajaService.save(pdvCaja);
                }
                if(!conteoMonedaInputList.isEmpty()){
                    for(ConteoMonedaInput conteoMonedaInput: conteoMonedaInputList){
                        conteoMonedaInput.setConteoId(conteo.getId());
                        conteoMonedaInput.setUsuarioId(input.getUsuarioId());
                        conteoMonedaGraphQL.saveConteoMoneda(conteoMonedaInput);
                    }
                }
            } else {
                pdvCajaService.deleteById(pdvCaja.getId());
            }
        }
        log.info("retornando conteo: "+ conteo.getId());
        return conteo;
    }

    public Boolean deleteConteo(Long id){
        return service.deleteById(id);
    }

    public Long countConteo(){
        return service.count();
    }


}
