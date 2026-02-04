package com.franco.dev.graphql.administrativo.resolver;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MarcacionResolver implements GraphQLResolver<Marcacion> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Usuario usuario(Marcacion marcacion) {
        if (marcacion.getUsuario() != null && marcacion.getUsuario().getId() != null) {
            return usuarioService.findById(marcacion.getUsuario().getId()).orElse(null);
        }
        return null;
    }

    public Sucursal sucursalEntrada(Marcacion marcacion) {
        if (marcacion.getSucursalEntrada() != null && marcacion.getSucursalEntrada().getId() != null) {
            return sucursalService.findById(marcacion.getSucursalEntrada().getId()).orElse(null);
        }
        return null;
    }

    public Sucursal sucursalSalida(Marcacion marcacion) {
        if (marcacion.getSucursalSalida() != null && marcacion.getSucursalSalida().getId() != null) {
            return sucursalService.findById(marcacion.getSucursalSalida().getId()).orElse(null);
        }
        return null;
    }
}
