package com.franco.dev.graphql.administrativo.resolver;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JornadaResolver implements GraphQLResolver<Jornada> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MarcacionService marcacionService;

    public Usuario usuario(Jornada jornada) {
        if (jornada.getUsuario() != null && jornada.getUsuario().getId() != null) {
            return usuarioService.findById(jornada.getUsuario().getId()).orElse(null);
        }
        return null;
    }

    public Marcacion marcacionEntrada(Jornada jornada) {
        if (jornada.getMarcacionEntrada() != null && jornada.getMarcacionEntrada().getId() != null) {
            return marcacionService.findById(new EmbebedPrimaryKey(jornada.getMarcacionEntrada().getId(),
                    jornada.getMarcacionEntrada().getSucursalId())).orElse(null);
        }
        return null;
    }

    public Marcacion marcacionSalida(Jornada jornada) {
        if (jornada.getMarcacionSalida() != null && jornada.getMarcacionSalida().getId() != null) {
            return marcacionService.findById(new EmbebedPrimaryKey(jornada.getMarcacionSalida().getId(),
                    jornada.getMarcacionSalida().getSucursalId())).orElse(null);
        }
        return null;
    }

    public Marcacion marcacionSalidaAlmuerzo(Jornada jornada) {
        if (jornada.getMarcacionSalidaAlmuerzo() != null && jornada.getMarcacionSalidaAlmuerzo().getId() != null) {
            return marcacionService.findById(new EmbebedPrimaryKey(jornada.getMarcacionSalidaAlmuerzo().getId(),
                    jornada.getMarcacionSalidaAlmuerzo().getSucursalId())).orElse(null);
        }
        return null;
    }

    public Marcacion marcacionEntradaAlmuerzo(Jornada jornada) {
        if (jornada.getMarcacionEntradaAlmuerzo() != null && jornada.getMarcacionEntradaAlmuerzo().getId() != null) {
            return marcacionService.findById(new EmbebedPrimaryKey(jornada.getMarcacionEntradaAlmuerzo().getId(),
                    jornada.getMarcacionEntradaAlmuerzo().getSucursalId())).orElse(null);
        }
        return null;
    }
}
