package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.graphql.financiero.input.PdvCajaInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.ConteoService;
import com.franco.dev.service.financiero.MaletinService;
import com.franco.dev.service.financiero.PdvCajaService;
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

import java.util.List;
import java.util.Optional;

@Component
public class PdvCajaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PdvCajaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ConteoService conteoService;

    @Autowired
    private MaletinService maletinService;

    @Autowired
    private ConteoGraphQL conteoGraphQL;

    @Autowired
    private ConteoMonedaGraphQL conteoMonedaGraphQL;

    public Optional<PdvCaja> pdvCaja(Long id) {return service.findById(id);}

    public List<PdvCaja> pdvCajas(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<PdvCaja> cajasPorFecha(String inicio, String fin){
        return service.findByDate(inicio, fin);
    }

//    public List<PdvCaja> searchCajaFilter(Long id, String fechaInicio, String fechaFin, Long maletinId, Long cajeroId){
//        if(id!=null){
//            return service.findById(id).orElse(null);
//        } else {
//
//        }
//    }


    public PdvCaja savePdvCaja(PdvCajaInput input){
        ModelMapper m = new ModelMapper();
        PdvCaja e = m.map(input, PdvCaja.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getConteoAperturaId()!=null) e.setConteoApertura(conteoService.findById(input.getConteoAperturaId()).orElse(null));
        if(input.getConteoCierreId()!=null) e.setConteoCierre(conteoService.findById(input.getConteoCierreId()).orElse(null));
        if(input.getMaletinId()!=null) e.setMaletin(maletinService.findById(input.getMaletinId()).orElse(null));
        PdvCaja pdvCaja = service.save(e);
        return pdvCaja;
    }

    //    public List<PdvCaja> pdvCajasSearch(String texto){
    //        return service.findByAll(texto);
    //    }

    public Boolean deletePdvCaja(Long id){
        return service.deleteById(id);
    }

    public Long countPdvCaja(){
        return service.count();
    }

    public PdvCaja cajaAbiertoPorUsuarioId(Long id){
        return service.findByUsuarioIdAndAbierto(id);
    }

    public PdvCaja imprimirBalance(Long id){
        return service.imprimirBalance(id);
    }

}
