package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.graphql.operaciones.input.NotaPedidoInput;
import com.franco.dev.service.operaciones.NotaPedidoService;
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
public class NotaPedidoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotaPedidoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PedidoService pedidoService;

    public Optional<NotaPedido> notaPedido(Long id) {return service.findById(id);}

    public List<NotaPedido> notaPedidos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

//    public List<NotaPedido> NotaPedidoSearch(String texto){
//        return service.findByAll(texto);
//    }

    public NotaPedido saveNotaPedido(NotaPedidoInput input){
        ModelMapper m = new ModelMapper();
        NotaPedido e = m.map(input, NotaPedido.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteNotaPedido(Long id){
        return service.deleteById(id);
    }

    public Long countNotaPedido(){
        return service.count();
    }


}
