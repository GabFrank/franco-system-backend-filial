package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.MotivoDiferenciaPedido;
import com.franco.dev.domain.operaciones.MotivoDiferenciaPedido;
import com.franco.dev.graphql.operaciones.input.MotivoDiferenciaPedidoInput;
import com.franco.dev.graphql.operaciones.input.MotivoDiferenciaPedidoInput;
import com.franco.dev.service.operaciones.MotivoDiferenciaPedidoService;
import com.franco.dev.service.operaciones.PedidoService;
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
public class MotivoDiferenciaPedidoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MotivoDiferenciaPedidoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PedidoService pedidoService;

    public Optional<MotivoDiferenciaPedido> motivoDiferenciaPedido(Long id) {return service.findById(id);}

    public List<MotivoDiferenciaPedido> motivoDiferenciaPedidos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

//    public List<MotivoDiferenciaPedido> MotivoDiferenciaPedidoSearch(String texto){
//        return service.findByAll(texto);
//    }

    public MotivoDiferenciaPedido saveMotivoDiferenciaPedido(MotivoDiferenciaPedidoInput input){
        ModelMapper m = new ModelMapper();
        MotivoDiferenciaPedido e = m.map(input, MotivoDiferenciaPedido.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteMotivoDiferenciaPedido(Long id){
        return service.deleteById(id);
    }

    public Long countMotivoDiferenciaPedido(){
        return service.count();
    }


}
