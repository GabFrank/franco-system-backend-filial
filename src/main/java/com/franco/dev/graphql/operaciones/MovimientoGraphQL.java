package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Necesidad;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.graphql.operaciones.input.MovimientoStockInput;
import com.franco.dev.graphql.operaciones.input.NecesidadInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.NecesidadService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.apache.tomcat.jni.Local;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class MovimientoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MovimientoStockService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private Environment env;

    public Optional<MovimientoStock> movimientoStock(Long id) {return service.findById(id);}

    public List<MovimientoStock> movimientosStock(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public MovimientoStock saveMovimientoStock(MovimientoStockInput input){
        ModelMapper m = new ModelMapper();
        MovimientoStock e = m.map(input, MovimientoStock.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        return service.saveAndSend(e, false);
    }

    public Boolean deleteMovimientoStock(Long id){
        return service.deleteById(id);
    }

    public Long countMovimientoStock(){
        return service.count();
    }

    public List<MovimientoStock> movimientoStockByFecha(String inicio, String fin){
        return service.findByDate(inicio, fin);
    }

    public void findByTipoMovimientoAndReferencia(TipoMovimiento tipoMovimiento, Long referencia){
        MovimientoStock movimientoStock = service.findByTipoMovimientoAndReferencia(tipoMovimiento, referencia);
        if(movimientoStock!=null){
            deleteMovimientoStock(movimientoStock.getId());
        }
    }

    public Float stockPorProducto(Long id){
        return service.stockByProductoId(id);
    }

}
