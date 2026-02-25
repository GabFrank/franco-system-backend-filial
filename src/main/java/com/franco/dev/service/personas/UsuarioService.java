package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.repository.personas.RoleRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.franco.dev.graphql.personas.UsuarioSimilitudResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class UsuarioService extends CrudService<Usuario, UsuarioRepository> {

    @Autowired
    private final UsuarioRepository repository;

    @Autowired
    private final RoleRepository roleRepository;

    @Autowired
    private final UsuarioRoleService usuarioRoleService;

    @Autowired
    private final RoleService roleService;

    @Autowired
    private final PersonaService personaService;

    @Autowired
    private final com.franco.dev.service.utils.ImageService imageService;

    @Autowired
    private final com.franco.dev.service.personas.CentralPersonasIntegrationService centralPersonasIntegrationService;

    @Override
    public UsuarioRepository getRepository() {
        return repository;
    }

    public Usuario findByPersonaId(Long id) {
        return repository.findByPersonaId(id);
    }

    public List<Usuario> findbyIdOrPersona(String texto) {
        texto = texto != null ? texto.trim() : "";

        if (!texto.isEmpty() && texto.chars().allMatch(Character::isDigit)) {
            try {
                Long personaId = Long.valueOf(texto);
                Usuario usuario = repository.findByPersonaId(personaId);
                if (usuario != null) {
                    List<Usuario> resultado = new ArrayList<>();
                    resultado.add(usuario);
                    return resultado;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        texto = texto.replace(' ', '%');
        return repository.findbyIdOrPersona(texto.toUpperCase());
    }

    public List<Role> getRoles(Long id) {
        List<UsuarioRole> usuarioRoleList = usuarioRoleService.findByUserId(id);
        List<Role> roleList = new ArrayList<Role>();
        if (!usuarioRoleList.isEmpty()) {
            usuarioRoleList.forEach(usuarioRole -> {
                Role role = roleService.findById(usuarioRole.getUser().getId()).orElse(null);
                if (role != null) {
                    roleList.add(role);
                }
            });
        }
        return roleList;
    }

    public Usuario findByEmail(String email) {
        return repository.findByEmail(email).orElse(null);
    }

    public Boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    public Boolean existsByNickname(String nickname) {
        return repository.existsByNickname(nickname);
    }

    public Optional<Usuario> findByNickname(String nickname) {
        return repository.findByNicknameIgnoreCase(nickname.toUpperCase());
    }

    @Override
    public Usuario save(Usuario entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        entity.setNickname(entity.getNickname().toUpperCase());
        if (entity.getPassword() != null)
            entity.setPassword(entity.getPassword().toUpperCase());
        Usuario e = repository.save(entity);
        return e;
    }

    public List<Usuario> saveAll(List<Usuario> entityList) {
        List<Usuario> usuarioList = new ArrayList<>();
        for (Usuario u : entityList) {
            Persona persona = null;
            if (u.getPersona() != null)
                persona = personaService.findById((long) u.getPersona().getId()).orElse(null);
            if (persona == null)
                u.setPersona(null);
            usuarioList.add(save(u));
        }
        return usuarioList;
    }

    public List<Usuario> findAllActivos() {
        return repository.findAllActivos();
    }

    public UsuarioSimilitudResult findUsuarioByEmbedding(List<Double> embeddingInfo,
            List<Integer> excludeIds) {
        List<Usuario> usuarios = repository.findAllActivos();
        Usuario bestMatch = null;
        Double maxSimilarity = -1.0;

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (Usuario usuario : usuarios) {
            if (excludeIds != null && !excludeIds.isEmpty() && excludeIds.contains(usuario.getId().intValue())) {
                continue;
            }

            if (usuario.getPersona() != null && usuario.getPersona().getEmbedding() != null) {
                try {
                    List<Double> storedEmbedding = mapper.readValue(usuario.getPersona().getEmbedding(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<Double>>() {
                            });

                    Double similarity = cosineSimilarity(embeddingInfo, storedEmbedding);

                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity;
                        bestMatch = usuario;
                    }
                } catch (Exception e) {
                    log.warn("Error parsing embedding for user {}: {}", usuario.getId(), e.getMessage());
                }
            }
        }

        if (bestMatch != null)

        {
            if (maxSimilarity > 0.75) {
                return new UsuarioSimilitudResult(bestMatch, maxSimilarity);
            }
        }
        return null;
    }

    public List<String> getUserImages(Long id, String type) {
        List<String> images = new java.util.ArrayList<>();
        Usuario usuario = repository.findById(id).orElse(null);
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
                            log.debug("No se pudo descargar imagen del servidor central para persona {}: {}",
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

    /**
     * Guarda una imagen de usuario y su embedding facial.
     *
     * @param id        ID del usuario.
     * @param type      Tipo de imagen (ej. "perfil").
     * @param image     Imagen en formato base64.
     * @param embedding Embedding facial (lista de dobles).
     * @return true si se guardó correctamente, false en caso contrario.
     * @throws java.io.IOException Si ocurre un error al guardar la imagen.
     */
    public Boolean saveUserImage(Long id, String type, String image, List<Double> embedding)
            throws java.io.IOException {
        log.info("Saving user image for id: {}, type: {}", id, type);
        try {
            String directoryPath = imageService.storageDirectoryPath + java.io.File.separator + "personas"
                    + java.io.File.separator + type + java.io.File.separator;
            java.io.File dir = new java.io.File(directoryPath);
            if (dir.exists() && dir.isDirectory()) {
                java.io.File[] existingFiles = dir.listFiles((d, name) -> name.startsWith(id + "_" + type));
                if (existingFiles != null) {
                    for (java.io.File file : existingFiles) {
                        log.debug("Deleting old image: {}", file.getName());
                        file.delete();
                    }
                }
            } else {
                dir.mkdirs();
            }
        } catch (Exception e) {
            log.error("Error deleting old images", e);
        }

        String fileName = id + "_" + type + System.currentTimeMillis() + ".png";
        Boolean saved = imageService.saveImageToPath(image, fileName,
                imageService.storageDirectoryPath + java.io.File.separator + "personas" + java.io.File.separator + type
                        + java.io.File.separator,
                imageService.storageDirectoryPath + java.io.File.separator + "personas" + java.io.File.separator + type
                        + java.io.File.separator + "thumb",
                true);

        if (saved) {
            Usuario usuario = repository.findById(id).orElse(null);
            if (usuario != null && usuario.getPersona() != null) {
                com.franco.dev.domain.personas.Persona persona = usuario.getPersona();
                persona.setImagenes(fileName);

                // Guardar embedding como JSON string
                if (embedding != null && !embedding.isEmpty()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        persona.setEmbedding(mapper.writeValueAsString(embedding));
                    } catch (Exception e) {
                        log.error("Error serializing embedding", e);
                    }
                }

                personaService.saveLocal(persona);

                // Sincronizar con el servidor central (incluyendo embedding si el método lo
                // soporta)
                // Por ahora mantenemos la firma existente de syncImageMetadata
                centralPersonasIntegrationService.syncImageMetadata(persona.getId(), fileName);
            }
        }
        return saved;
    }

    private Double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }

        if (normA == 0 || normB == 0)
            return 0.0;

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
