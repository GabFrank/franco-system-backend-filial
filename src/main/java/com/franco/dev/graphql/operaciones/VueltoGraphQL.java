package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.domain.operaciones.Vuelto;
import com.franco.dev.graphql.operaciones.input.NotaPedidoInput;
import com.franco.dev.graphql.operaciones.input.VueltoInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.NotaPedidoService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.VueltoService;
import com.franco.dev.service.personas.FuncionarioService;
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
public class VueltoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VueltoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    public Optional<Vuelto> vuelto(Long id) {return service.findById(id);}

    public List<Vuelto> vueltos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

//    public List<Vuelto> VueltoSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Vuelto saveVuelto(VueltoInput input){
        ModelMapper m = new ModelMapper();
        Vuelto e = m.map(input, Vuelto.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getResponsableId()!=null){
            e.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));
        }
        if(input.getAutorizadoPorId()!=null){
            e.setAutorizadoPor(funcionarioService.findById(input.getAutorizadoPorId()).orElse(null));
        }
        return service.save(e);
    }

    public Boolean deleteVuelto(Long id){
        return service.deleteById(id);
    }

    public Long countVuelto(){
        return service.count();
    }


}
