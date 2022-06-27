package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.graphql.operaciones.input.PedidoInput;
import com.franco.dev.graphql.operaciones.input.PedidoItemInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.personas.VendedorService;
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
public class PedidoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PedidoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private VendedorService vendedorService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private PedidoItemGraphQL pedidoItemGraphQL;

    public Optional<Pedido> pedido(Long id) {return service.findById(id);}

    public List<Pedido> pedidos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Pedido> filterPedidos(PedidoEstado estado, Long sucursalId, String inicio, String fin, Long proveedorId, Long vendedorId, Long formaPago, Long productoId){
        String auxEstado = null;
        String auxFormaPago = null;
        if(estado!=null){
            auxEstado = estado.name();
        }
        if(fin==null && inicio!=null){
            fin = inicio;
        }
        return service.filterPedidos(auxEstado, sucursalId, inicio, fin, proveedorId, vendedorId, auxFormaPago, productoId);
    }

    public Pedido savePedido(PedidoInput input){
        ModelMapper m = new ModelMapper();
        Pedido e = m.map(input, Pedido.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getMonedaId()!=null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if(input.getProveedorId()!=null) e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if(input.getVendedorId()!=null) e.setVendedor(vendedorService.findById(input.getVendedorId()).orElse(null));
        Pedido pedido = service.save(e);
        if(pedido!=null){
            for(PedidoItemInput itemInput : input.getPedidoItemInputList()){
                itemInput.setPedidoId(pedido.getId());
                itemInput.setCreadoEn(pedido.getCreadoEn());
                pedidoItemGraphQL.savePedidoItem(itemInput);
            }
        }
        return pedido;
    }

    public Boolean deletePedido(Long id){
        return service.deleteById(id);
    }

    public Long countPedido(){
        return service.count();
    }


}
