package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.CompraItemEstado;
import com.franco.dev.graphql.operaciones.input.PedidoItemInput;
import com.franco.dev.service.operaciones.CompraItemService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
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
public class PedidoItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PedidoItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private CompraItemService compraItemService;

    public Optional<PedidoItem> pedidoItem(Long id) {return service.findById(id);}

    public List<PedidoItem> pedidoItens(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<PedidoItem> pedidoItensPorPedido(Long pedidoId){
        return service.findByPedidoId(pedidoId);
    }


    public PedidoItem savePedidoItem(PedidoItemInput input){
        ModelMapper m = new ModelMapper();
        PedidoItem e = m.map(input, PedidoItem.class);
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getProductoId()!=null) e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        if(input.getPedidoId()!=null) e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        if(input.getPresentacionId()!=null) e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        if(input.getNotaRecepcionId()!=null) e.setNotaRecepcion(notaRecepcionService.findById(input.getNotaRecepcionId()).orElse(null));
        if(input.getId()!=null){
            PedidoItem aux = service.findById(input.getId()).orElse(null);
            if(aux!=null && aux.getNotaRecepcion()!=null){
                e.setNotaRecepcion(aux.getNotaRecepcion());
            }
        }
        return service.save(e);
    }

    public Boolean deletePedidoItem(Long id){
        return service.deleteById(id);
    }

    public Long countPedidoItem(){
        return service.count();
    }

    public List<PedidoItem> pedidoItemPorPedidoIdSobrante(Long id){
        return service.findByPedidoIdSobrantes(id);
    }

    public PedidoItem updateNotaRecepcion(Long pedidoItemId, Long notaRecepcionId){
        PedidoItem pedidoItem = service.findById(pedidoItemId).orElse(null);
        if(pedidoItem!=null){
            if(notaRecepcionId!=null){
                pedidoItem.setNotaRecepcion(notaRecepcionService.findById(notaRecepcionId).orElse(null));
            } else {
                pedidoItem.setNotaRecepcion(null);
//                CompraItem compraItem = compraItemService.findByPedidoItemId(pedidoItemId);
//                if(compraItem!=null){
//                    compraItem.setEstado(CompraItemEstado.SIN_MODIFICACION);
//                    compraItem.setPrecioUnitario(pedidoItem.getPrecioUnitario());
//                    compraItem.setDescuentoUnitario(pedidoItem.getDescuentoUnitario());
//                    compraItem.setCantidad(pedidoItem.getCantidad());
//                    compraItemService.save(compraItem);
//                }
            }
            pedidoItem = service.save(pedidoItem);
        }
        return pedidoItem;
    }


}
