package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.graphql.personas.input.UsuarioInput;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.utils.ImageService;
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
public class UsuarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private UsuarioService service;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UsuarioRoleService usuarioRoleService;

    @Autowired
    private ImageService imageService;

    public Optional<Usuario> usuario(Long id) {
        return service.findById(id);
    }

    public Usuario usuarioPorPersonaId(Long id) {
        return service.findByPersonaId(id);
    }

    public List<Usuario> usuarioSearch(String texto) {
        return service.findbyIdOrPersona(texto);
    }

    public List<Usuario> usuarios(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Usuario saveUsuario(UsuarioInput input) {
        ModelMapper m = new ModelMapper();
        Usuario e = m.map(input, Usuario.class);
        if (input.getUsuarioId() != null)
            e.setUsuario(service.findById(input.getUsuarioId()).orElse(null));
        if (input.getPersonaId() != null)
            e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteUsuario(Long id) {
        return service.deleteById(id);
    }

    public Long countUsuario() {
        return service.count();
    }

    public List<String> getUsuarioImages(Long id, String type) {
        List<String> images = new java.util.ArrayList<>();
        Usuario usuario = service.findById(id).orElse(null);
        if (usuario != null && usuario.getPersona() != null && usuario.getPersona().getImagenes() != null) {
            String[] imgs = usuario.getPersona().getImagenes().split(",");
            for (String img : imgs) {
                if (!img.trim().isEmpty()) {
                    String path = imageService.storageDirectoryPath + java.io.File.separator + "personas"
                            + java.io.File.separator + "perfil" + java.io.File.separator;
                    String base64 = imageService.getImageWithMediaType(img.trim(), path);
                    if (base64 != null)
                        images.add(base64);
                }
            }
        }
        return images;
    }
}
