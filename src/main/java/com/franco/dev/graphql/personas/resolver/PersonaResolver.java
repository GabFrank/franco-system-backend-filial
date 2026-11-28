package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.*;
import com.franco.dev.service.seguridad.RolSesionService;
import com.franco.dev.service.seguridad.UsuarioSesionService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PersonaResolver implements GraphQLResolver<Persona> {

    @Autowired
    private PersonaService personaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private RolSesionService rolSesionService;

    @Autowired
    private UsuarioSesionService usuarioSesionService;

    @Value("${personas.embedding-facial.restringido:false}")
    private boolean embeddingRestringido;

    public Boolean isFuncionario(Persona p){
        return funcionarioService.findByPersonaId(p.getId())!=null;
    }

    public Boolean isCliente(Persona p){
        return clienteService.findByPersonaId(p.getId())!=null;
    }

    public Boolean isProveedor(Persona p){
        return proveedorService.findByPersonaId(p.getId())!=null;
    }

    public Boolean isUsuario(Persona p){
        return usuarioService.findByPersonaId(p.getId())!=null;
    }

    /** Con {@code personas.embedding-facial.restringido}: el titular, RRHH GESTIONAR o ADMIN; los demás, null. */
    public String embeddingFacial(Persona p) {
        if (!embeddingRestringido || p.getEmbedding() == null) return p.getEmbedding();
        if (!usuarioSesionService.idDelUsuarioActivoAutenticado().isPresent()) return null;
        Usuario duenio = usuarioService.findByPersonaId(p.getId());
        return rolSesionService.esDuenioOTieneRol(duenio != null ? duenio.getId() : null, "RRHH GESTIONAR")
                ? p.getEmbedding() : null;
    }
}
