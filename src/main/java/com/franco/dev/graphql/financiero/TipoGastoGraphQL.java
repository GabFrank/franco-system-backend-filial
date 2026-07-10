package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.financiero.enums.TipoPadreGastoModulo;
import com.franco.dev.graphql.financiero.dto.ModuloGastoInfo;
import com.franco.dev.graphql.financiero.input.TipoGastoInput;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.financiero.TipoGastoModuloReglasService;
import com.franco.dev.service.financiero.TipoGastoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TipoGastoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoGastoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CargoService cargoService;

    @Autowired
    private TipoGastoModuloReglasService moduloReglasService;

    public Optional<TipoGasto> tipoGasto(Long id) {return service.findById(id);}

    public List<TipoGasto> tipoGastos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<ModuloGastoInfo> modulosGasto() {
        return moduloReglasService.listarModulos();
    }

    public Page<TipoGasto> filterTipoGastos(String naturaleza, String texto, TipoPadreGastoModulo moduloPadre, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 15);
        String moduloPadreStr = moduloPadre != null ? moduloPadre.name() : null;
        return service.filterTipoGastos(naturaleza, texto, moduloPadreStr, pageable);
    }


    public TipoGasto saveTipoGasto(TipoGastoInput input){
        ModelMapper m = new ModelMapper();
        TipoGasto e = m.map(input, TipoGasto.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getCargoId()!=null) e.setCargo(cargoService.findById(input.getCargoId()).orElse(null));
        return service.save(e);
    }

    public List<TipoGasto> tipoGastosSearch(String texto){
        return service.findByAll(texto);
    }

    public Boolean deleteTipoGasto(Long id){
        return service.deleteById(id);
    }

    public Long countTipoGasto(){
        return service.count();
    }


}
