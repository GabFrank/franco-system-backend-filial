package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Necesidad;
import com.franco.dev.domain.operaciones.NecesidadItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.NecesidadItemService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class NecesidadResolver implements GraphQLResolver<Necesidad> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private NecesidadItemService necesidadItemService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private SucursalService sucursalService;

    public List<NecesidadItem> necesidadItens(Necesidad e) { return necesidadItemService.findByNecesidad(e.getId());}

    public String nombreSucursal(Necesidad e){
        return e.getSucursal().getNombre();
    }

    public String nombreUsuario(Necesidad e){
//        Usuario u = usuarioService.findById(e.getUsuario().getId()).orElse(null);
//        Persona p = personaService.findById(u.getPersona().getId()).orElse(null);
//        return p.getNombre();
        return e.getUsuario().getPersona().getNombre();
    }

//    public TipoConservacion tipoConservacion(Necesidad e){ return e.getTipoConservacion(); }
//    private Optional<SubFamilia> subFamilia(Necesidad e) {
//        return subFamiliaService.findById(e.getSubFamilia().getId());
//    }

}
