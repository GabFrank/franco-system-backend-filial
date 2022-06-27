package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Compra;
import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.CompraItemService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CompraResolver implements GraphQLResolver<Compra> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CompraItemService compraItemService;

    public List<CompraItem> compraItens(Compra e) { return compraItemService.findByCompraId(e.getId());}

    public Double valorTotal(Compra e) {
        List<CompraItem> pi = compraItemService.findByCompraId(e.getId());
        Double parcial = 0.0;
        for(CompraItem i : pi) {
            System.out.println(i.getPrecioUnitario() + "");
            parcial = parcial + ((i.getPrecioUnitario() - i.getDescuentoUnitario()) * i.getCantidad());
        }

        return parcial - e.getDescuento();
    }

    public String nombreProveedor(Compra e){
        return e.getProveedor().getPersona().getNombre();
    }

    public String nombreUsuario(Compra e){
        return e.getUsuario().getPersona().getNombre();
    }

//    public TipoConservacion tipoConservacion(Compra e){ return e.getTipoConservacion(); }
//    private Optional<SubFamilia> subFamilia(Compra e) {
//        return subFamiliaService.findById(e.getSubFamilia().getId());
//    }

}
