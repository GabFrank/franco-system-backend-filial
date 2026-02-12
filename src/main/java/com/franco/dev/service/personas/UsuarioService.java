package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.repository.personas.RoleRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.franco.dev.graphql.personas.UsuarioSimilitudResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
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

    @Override
    public UsuarioRepository getRepository() {
        return repository;
    }

    public Usuario findByPersonaId(Long id) {
        return repository.findByPersonaId(id);
    }


    public List<Usuario> findbyIdOrPersona(String texto) {
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
        if(entity.getPassword()!=null) entity.setPassword(entity.getPassword().toUpperCase());
        Usuario e = repository.save(entity);
        return e;
    }

    public List<Usuario> saveAll(List<Usuario> entityList) {
        List<Usuario> usuarioList = new ArrayList<>();
        for (Usuario u : entityList) {
            Persona persona = null;
            if(u.getPersona()!=null) persona = personaService.findById((long) u.getPersona().getId()).orElse(null);
            if (persona == null) u.setPersona(null);
            usuarioList.add(save(u));
        }
        return usuarioList;
    }

    /**
     * Obtiene todos los usuarios activos ordenados por nombre
     */
    public List<Usuario> findAllActivos() {
        return repository.findAllActivos();
    }

    /**
     * Busca un usuario por similitud de embedding facial (reconocimiento facial).
     * Retorna el usuario con mayor similitud si supera el umbral de 0.75.
     */
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
                    org.slf4j.LoggerFactory.getLogger(UsuarioService.class)
                            .warn("Error parsing embedding for user {}: {}", usuario.getId(), e.getMessage());
                }
            }
        }

        if (bestMatch != null) {
            if (maxSimilarity > 0.75) {
                return new UsuarioSimilitudResult(bestMatch, maxSimilarity);
            }
        }
        return null;
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
