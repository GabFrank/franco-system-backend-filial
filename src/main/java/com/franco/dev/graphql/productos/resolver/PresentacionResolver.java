package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.*;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.*;
import com.franco.dev.service.utils.ImageService;
import graphql.kickstart.tools.GraphQLResolver;
import kotlin.collections.ArrayDeque;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class PresentacionResolver implements GraphQLResolver<Presentacion> {

    @Autowired
    private ImageService imageService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;

    @Autowired
    private PrecioEspecialLector precioEspecialLector;

    /** Para vistas grandes. Mismo campo que el central: el escritorio consulta a los dos. */
    public String imagenPrincipalMediana(Presentacion p) {
        return imageService.fotoPresentacion(p.getId(), true);
    }

    public String imagenPrincipal(Presentacion p) throws IOException {
        return imageService.getImageWithMediaType(p.getId()+".jpg", imageService.getImagePresentacionesThumbPath());
    }

    public List<Codigo> codigos(Presentacion p){
        return codigoService.findByPresentacionId(p.getId());
    }

    public List<PrecioPorSucursal> precios(Presentacion p){
        return precioEspecialLector.aplicar(precioPorSucursalService.findByPresentacionId(p.getId()));
    }

    public Codigo codigoPrincipal(Presentacion p){
        return codigoService.findPrincipalByPresentacionId(p.getId());
    }

    /**
     * El principal sale de la lista ya resuelta, para que refleje el precio especial. La query
     * nativa findPrincipalByPresentacionId hacia select * sobre un join presentacion/precio con
     * columnas homonimas (id, activo, principal): el id mapeado podia ser el de la presentacion.
     */
    public PrecioPorSucursal precioPrincipal(Presentacion p){
        for (PrecioPorSucursal precio : precios(p)) {
            if (precio != null && Boolean.TRUE.equals(precio.getPrincipal())) return precio;
        }
        return null;
    }

}
