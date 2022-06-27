package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemSucursal;
import com.franco.dev.graphql.operaciones.input.PedidoItemInput;
import com.franco.dev.graphql.operaciones.input.PedidoItemSucursalInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoItemSucursalService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
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
public class PedidoItemSucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PedidoItemSucursalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PedidoItemService pedidoItemService;

    public Optional<PedidoItemSucursal> pedidoItemSucursal(Long id) {return service.findById(id);}

    public List<PedidoItemSucursal> pedidoItensSucursal(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public PedidoItemSucursal savePedidoItemSucursal(PedidoItemSucursalInput input){
        ModelMapper m = new ModelMapper();
        PedidoItemSucursal e = m.map(input, PedidoItemSucursal.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e.setSucursalEntrega(sucursalService.findById(input.getSucursalEntregaId()).orElse(null));
        e.setPedidoItem(pedidoItemService.findById(input.getPedidoItemId()).orElse(null));
        return service.save(e);
    }

    public Boolean deletePedidoItemSucursal(Long id){
        return service.deleteById(id);
    }

    public Long countPedidoItemSucursal(){
        return service.count();
    }


}
