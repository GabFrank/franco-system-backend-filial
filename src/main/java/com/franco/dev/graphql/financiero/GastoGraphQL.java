package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.GastoDetalle;
import com.franco.dev.graphql.financiero.input.GastoDetalleInput;
import com.franco.dev.graphql.financiero.input.GastoInput;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.TipoGastoService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.dto.GastoDto;
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
public class GastoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private GastoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private GastoDetalleGraphQL gastoDetalleGraphQL;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private TipoGastoService tipoGastoService;

    public Optional<Gasto> gasto(Long id) {return service.findById(id);}

    public List<Gasto> gastos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Gasto> gastosPorCajaId(Long id){
        return service.findByCajaId(id);
    }

    public List<Gasto> gastosPorFecha(String inicio, String fin){
        return service.findByDate(inicio, fin);
    }

    public Gasto saveGasto(GastoInput input) throws GraphQLException {
        ModelMapper m = new ModelMapper();
        Gasto e = m.map(input, Gasto.class);

        if(input.getFinalizado()==true){
            e = gasto(input.getId()).orElse(null);
            e.setVueltoGs(input.getVueltoGs());
            e.setVueltoRs(input.getVueltoRs());
            e.setVueltoDs(input.getVueltoDs());
            e.setFinalizado(true);
        } else {
            if (input.getUsuarioId() != null) {
                e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
            }
            if (input.getCajaId() != null) e.setCaja(pdvCajaService.findById(input.getCajaId()).orElse(null));
            if (input.getAutorizadoPorId() != null)
                e.setAutorizadoPor(funcionarioService.findById(input.getAutorizadoPorId()).orElse(null));
            if (input.getResponsableId() != null)
                e.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));
            if (input.getTipoGastoId() != null)
                e.setTipoGasto(tipoGastoService.findById(input.getTipoGastoId()).orElse(null));
        }
        Gasto gasto = service.save(e);
        GastoDto gastoDto = new GastoDto();
        if(gasto!=null && input.getFinalizado()!=true){
            gastoDto.setId(gasto.getId());
            gastoDto.setFecha(gasto.getCreadoEn());
            gastoDto.setUsuario(gasto.getUsuario());
            gastoDto.setResponsable(gasto.getResponsable());
            gastoDto.setAutorizadoPor(gasto.getAutorizadoPor());
            gastoDto.setTipoGasto(gasto.getTipoGasto());
            gastoDto.setObservacion(gasto.getObservacion());
            gastoDto.setRetiroGs(input.getRetiroGs());
            gastoDto.setRetiroRs(input.getRetiroRs());
            gastoDto.setRetiroDs(input.getRetiroDs());
            gastoDto.setVueltoGs(input.getVueltoGs());
            gastoDto.setVueltoRs(input.getVueltoRs());
            gastoDto.setVueltoDs(input.getVueltoDs());
            gastoDto.setCajaId(gasto.getCaja().getId());
            impresionService.printGasto(gastoDto);
        }
        return gasto;
    }

//    public List<Gasto> gastosSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteGasto(Long id){
        return service.deleteById(id);
    }

    public Long countGasto(){
        return service.count();
    }


}
