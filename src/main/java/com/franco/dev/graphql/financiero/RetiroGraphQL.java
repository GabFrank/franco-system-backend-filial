package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.RetiroDetalleInput;
import com.franco.dev.graphql.financiero.input.RetiroInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RetiroGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RetiroService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private RetiroDetalleGraphQL retiroDetalleGraphQL;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private FuncionarioService funcionarioService;

    public Optional<Retiro> retiro(Long id) {return service.findById(id);}

    public List<Retiro> retiros(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Retiro> retiroListPorCajaSalidaId(Long id){
        return service.findByCajaSalidaId(id);
    }


    public Retiro saveRetiro(RetiroInput input, List<RetiroDetalleInput> retiroDetalleInputList, String printerName, String local) throws GraphQLException {
        ModelMapper m = new ModelMapper();
        Retiro e = m.map(input, Retiro.class);

        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getCajaSalidaId()!=null) e.setCajaSalida(pdvCajaService.findById(input.getCajaSalidaId()).orElse(null));
        if(input.getCajaEntradaId()!=null) e.setCajaEntrada(pdvCajaService.findById(input.getCajaEntradaId()).orElse(null));
        if(input.getResponsableId()!=null) e.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));

        Retiro retiro = service.save(e);

        if(retiro!=null){
            for(RetiroDetalleInput r: retiroDetalleInputList){
                r.setRetiroId(retiro.getId());
                r.setUsuarioId(retiro.getUsuario().getId());
                r.setCreadoEn(retiro.getCreadoEn());
                retiroDetalleGraphQL.saveRetiroDetalle(r);
            }

            RetiroDto retiroDto = new RetiroDto();
            retiroDto.setId(retiro.getId());
            retiroDto.setCajaId(retiro.getCajaSalida().getId());
            retiroDto.setFecha(retiro.getCreadoEn());
            retiroDto.setResponsable(retiro.getResponsable());
            retiroDto.setRetiroGs(input.getRetiroGs());
            retiroDto.setRetiroRs(input.getRetiroRs());
            retiroDto.setRetiroDs(input.getRetiroDs());
            retiroDto.setUsuario(retiro.getUsuario());
            impresionService.printRetiro(retiroDto, printerName, local);
        }
        return retiro;
    }

//    public List<Retiro> retirosSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteRetiro(Long id){
        return service.deleteById(id);
    }

    public Long countRetiro(){
        return service.count();
    }


}
