package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.graphql.operaciones.input.NotaPedidoInput;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionInput;
import com.franco.dev.service.financiero.DocumentoService;
import com.franco.dev.service.operaciones.CompraService;
import com.franco.dev.service.operaciones.NotaPedidoService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
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
public class NotaRecepcionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotaRecepcionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CompraService compraService;
    @Autowired
    private DocumentoService documentoService;
    @Autowired
    private PedidoService pedidoService;

    public Optional<NotaRecepcion> notaRecepcion(Long id) {return service.findById(id);}

    public List<NotaRecepcion> notaRecepcions(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<NotaRecepcion> notaRecepcionPorPedidoId(Long id){
        return service.findByPedidoId(id);
    }

    public NotaRecepcion saveNotaRecepcion(NotaRecepcionInput input){
        ModelMapper m = new ModelMapper();
        NotaRecepcion e = m.map(input, NotaRecepcion.class);
        if(input.getCompraId()!=null) e.setCompra(compraService.findById(input.getCompraId()).orElse(null));
        if(input.getDocumentoId()!=null) e.setDocumento(documentoService.findById(input.getDocumentoId()).orElse(null));
        if(input.getPedidoId()!=null) e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteNotaRecepcion(Long id){
        return service.deleteById(id);
    }

    public Long countNotaRecepcion(){
        return service.count();
    }


}
