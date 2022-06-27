package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Vuelto;
import com.franco.dev.domain.operaciones.VueltoItem;
import com.franco.dev.graphql.operaciones.input.VueltoInput;
import com.franco.dev.graphql.operaciones.input.VueltoItemInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.VueltoItemService;
import com.franco.dev.service.operaciones.VueltoService;
import com.franco.dev.service.personas.UsuarioService;
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
public class VueltoItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VueltoItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private VueltoService vueltoService;

    public Optional<VueltoItem> vueltoItem(Long id) {return service.findById(id);}

    public List<VueltoItem> vueltoItems(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

//    public List<VueltoItem> VueltoItemSearch(String texto){
//        return service.findByAll(texto);
//    }

    public VueltoItem saveVueltoItem(VueltoItemInput input){
        ModelMapper m = new ModelMapper();
        VueltoItem e = m.map(input, VueltoItem.class);
        if(input.getUsuarioId()!= null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getMonedaId()!= null){
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if(input.getVueltoId()!= null){
            e.setVuelto(vueltoService.findById(input.getVueltoId()).orElse(null));
        }
        return service.save(e);
    }

    public Boolean deleteVueltoItem(Long id){
        return service.deleteById(id);
    }

    public Long countVueltoItem(){
        return service.count();
    }


}
