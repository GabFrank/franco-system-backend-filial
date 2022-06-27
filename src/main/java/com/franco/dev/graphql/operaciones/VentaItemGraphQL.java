package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class VentaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VentaItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;

    public Optional<VentaItem> ventaItem(Long id) {return service.findById(id);}

    public List<VentaItem> ventaItems(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

//    public List<VentaItem> ventaItemSearch(String texto){
//        return service.findByAll(texto);
//    }

    public VentaItem saveVentaItem(VentaItemInput input){
        ModelMapper m = new ModelMapper();
        Venta venta = ventaService.findById(input.getVentaId()).orElse(null);
        VentaItem e = m.map(input, VentaItem.class);
        if(e.getUsuario()!=null) e.setUsuario(venta.getUsuario());
        if(e.getProducto()!=null) e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        if(e.getPresentacion()!=null) e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        if(e.getVenta()!=null) e.setVenta(venta);
        if(e.getPrecioVenta()!=null) e.setPrecioVenta(precioPorSucursalService.findById(input.getPrecioVentaId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteVentaItem(Long id){
        return service.deleteById(id);
    }

    public Long countVentaItem(){
        return service.count();
    }

    public List<VentaItem> saveVentaItemList(List<VentaItemInput> ventaItemInputList, Long ventaId){
        int index = 0;
        List<VentaItem> ventaItemList = new ArrayList<>();
        for(VentaItemInput v : ventaItemInputList){
            v.setVentaId(ventaId);
            v.setActivo(true);
            VentaItem vi = saveVentaItem(v);
            ventaItemList.add(vi);
            index++;
        }
        return ventaItemList;
    }

    public Boolean cancelarVentaItens(Long id){
        List<VentaItem> ventaItemList = service.findByVentaId(id);
        for(VentaItem vi: ventaItemList){
            vi.setActivo(false);
            service.save(vi);
        }
        return true;
    }

    public List<VentaItem> ventaItemListPorVentaId(Long id){
        return service.findByVentaId(id);
    }

}
