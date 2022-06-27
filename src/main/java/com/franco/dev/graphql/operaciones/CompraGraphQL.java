package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.operaciones.Compra;
import com.franco.dev.graphql.operaciones.input.CompraInput;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.CompraService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.ProveedorService;
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
public class CompraGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CompraService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private FormaPagoService formaPagoService;

    public Optional<Compra> compra(Long id) {return service.findById(id);}

    public List<Compra> compras(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Compra saveCompra(CompraInput input){
        ModelMapper m = new ModelMapper();
        Compra e = m.map(input, Compra.class);
        if(input.getMonedaId()!=null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getPedidoId()!=null) e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        if(input.getProveedorId()!=null) e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        if(input.getFormaPagoId()!=null) e.setFormaPago(formaPagoService.findById(input.getFormaPagoId()).orElse(null));
        return service.save(e);
    }

    public List<Compra> comprasPorProducto(Long id){
        return service.findByProductoId(id);
    }

    public Boolean deleteCompra(Long id){
        return service.deleteById(id);
    }

    public Long countCompra(){
        return service.count();
    }

}
