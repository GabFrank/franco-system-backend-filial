package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends HelperRepository<Usuario, Long> {

    default Class<Usuario> getEntityClass() {
        return Usuario.class;
    }

    Usuario findByPersonaId(Long id);

    @Query("select u from Usuario u " +
            "join u.persona p " +
            "where CAST(u.id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(u.nickname) like %?1%")
    public List<Usuario> findbyIdOrPersona(String texto);

    public boolean existsByEmail(String email);

    public boolean existsByNickname(String nickname);

    public Optional<Usuario> findByNicknameIgnoreCase(String nickname);

    public Optional<Usuario> findByEmail(String email);



}
