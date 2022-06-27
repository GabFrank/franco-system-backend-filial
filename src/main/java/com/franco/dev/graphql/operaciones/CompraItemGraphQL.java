package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Compra;
import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.graphql.operaciones.input.CompraItemInput;
import com.franco.dev.service.operaciones.CompraItemService;
import com.franco.dev.service.operaciones.CompraService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.ProgramarPrecioService;
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
public class CompraItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CompraItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CompraService compraService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private ProgramarPrecioService programarPrecioService;

    public Optional<CompraItem> compraItem(Long id) {return service.findById(id);}

    public List<CompraItem> compraItemPorProductoId(Long id){ return service.findByProductoId(id) ;}

    public List<CompraItem> compraItemPorCompraId(Long id){ return service.findByCompraId(id) ;}

    public List<CompraItem> compraItens(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public CompraItem saveCompraItem(CompraItemInput input){
        ModelMapper m = new ModelMapper();
        CompraItem e = m.map(input, CompraItem.class);
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getProductoId()!=null) e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        if(input.getCompraId()!=null){
            e.setCompra(compraService.findById(input.getCompraId()).orElse(null));
        } else {
            e.setCompra(null);
        }
        if(input.getPresentacionId()!=null) e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        if(input.getPedidoItemId()!=null) e.setPedidoItem(pedidoItemService.findById(input.getPedidoItemId()).orElse(null));
        if(input.getProgramarPrecioId()!=null) e.setProgramarPrecio(programarPrecioService.findById(input.getProgramarPrecioId()).orElse(null));
        CompraItem compraItem = service.save(e);
        return compraItem;
    }

    public Boolean deleteCompraItem(Long id){
        return service.deleteById(id);
    }

    public Long countCompraItem(){
        return service.count();
    }


}
