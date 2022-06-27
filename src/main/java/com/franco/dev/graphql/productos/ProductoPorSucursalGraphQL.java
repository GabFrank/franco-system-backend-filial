package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.ProductoPorSucursal;
import com.franco.dev.graphql.productos.input.PrecioPorSucursalInput;
import com.franco.dev.graphql.productos.input.ProductoPorSucursalInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.ProductoPorSucursalService;
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
public class ProductoPorSucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProductoPorSucursalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<ProductoPorSucursal> productoPorSucursal(Long id) {return service.findById(id);}

    public List<ProductoPorSucursal> productoPorSucursalPorProductoId(Long id){ return service.findByProductoId(id);}
    public List<ProductoPorSucursal> productoPorSucursalPorSucursalId(Long id){ return service.findBySucursalId(id);}
    public ProductoPorSucursal productoPorSucursalPorProIdSucId(Long prodId, Long sucId){ return service.findByProIdSucId(prodId, sucId);}

    //    public List<ProductoPorSucursal> productoPorSucursalPorProductoPorSucursal(String texto) {return
    //            service.findByProductoPorSucursal(texto);
    //    }

    public List<ProductoPorSucursal> productosPorSucursales(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public ProductoPorSucursal saveProductoPorSucursal(ProductoPorSucursalInput input){
        ModelMapper m = new ModelMapper();
        ProductoPorSucursal e = m.map(input, ProductoPorSucursal.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteProductoPorSucursal(Long id){
        return service.deleteById(id);
    }

    public Long countProductoPorSucursal(){
        return service.count();
    }
}
