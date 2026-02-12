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

    @Autowired
    private com.franco.dev.service.personas.CentralPersonasIntegrationService centralPersonasIntegrationService;

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

    public Boolean verificarUsuario(String nickname) {
        return service.existsByNickname(nickname);
    }

    public List<Usuario> usuariosActivos() {
        return service.findAllActivos();
    }

    public UsuarioSimilitudResult usuarioPorEmbedding(List<Double> embedding, List<Integer> excludeIds) {
        return service.findUsuarioByEmbedding(embedding, excludeIds);
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
                    if (base64 == null) {
                        try {
                            byte[] dlBytes = centralPersonasIntegrationService
                                    .downloadImage(usuario.getPersona().getId());
                            if (dlBytes != null && dlBytes.length > 0) {
                                java.nio.file.Path filePath = java.nio.file.Paths.get(path, img.trim());
                                java.nio.file.Files.createDirectories(filePath.getParent());
                                java.nio.file.Files.write(filePath, dlBytes);
                                base64 = imageService.getImageWithMediaType(img.trim(), path);
                            }
                        } catch (Exception e) {
                            org.slf4j.LoggerFactory.getLogger(UsuarioGraphQL.class)
                                    .debug("No se pudo descargar imagen del servidor central para persona {}: {}",
                                            usuario.getPersona().getId(), e.getMessage());
                        }
                    }

                    if (base64 != null)
                        images.add(base64);
                }
            }
        }
        return images;
    }

    public Boolean saveUserImage(Long id, String type, String image) throws java.io.IOException {
        System.out.println("Saving user image for id: " + id + ", type: " + type);
        try {
            String directoryPath = imageService.storageDirectoryPath + java.io.File.separator + "personas"
                    + java.io.File.separator + type + java.io.File.separator;
            java.io.File dir = new java.io.File(directoryPath);
            if (dir.exists() && dir.isDirectory()) {
                java.io.File[] existingFiles = dir.listFiles((d, name) -> name.startsWith(id + "_" + type));
                if (existingFiles != null) {
                    for (java.io.File file : existingFiles) {
                        System.out.println("Deleting old image: " + file.getName());
                        file.delete();
                    }
                }
            } else {
                dir.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String fileName = id + "_" + type + System.currentTimeMillis() + ".png";
        Boolean saved = imageService.saveImageToPath(image, fileName,
                imageService.storageDirectoryPath + java.io.File.separator + "personas" + java.io.File.separator + type
                        + java.io.File.separator,
                imageService.storageDirectoryPath + java.io.File.separator + "personas" + java.io.File.separator + type
                        + java.io.File.separator + "thumb",
                true);

        if (saved) {
            Usuario usuario = service.findById(id).orElse(null);
            if (usuario != null && usuario.getPersona() != null) {
                com.franco.dev.domain.personas.Persona persona = usuario.getPersona();
                persona.setImagenes(fileName);
                personaService.saveLocal(persona);

                centralPersonasIntegrationService.syncImageMetadata(
                        persona.getId(),
                        fileName);
            }
        }
        return saved;
    }
}
