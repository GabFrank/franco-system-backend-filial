package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FuncionarioRepository extends HelperRepository<Funcionario, Long> {

    default Class<Funcionario> getEntityClass() {
        return Funcionario.class;
    }

    public Funcionario findByPersonaId(Long id);

    public Funcionario findByUsuarioId(Long id);

    @Query("select u from Funcionario u " +
            "join u.persona p " +
            "where CAST(u.id as text) like %?1% or UPPER(p.nombre) like %?1%")
    public List<Funcionario> findByIdOrPersonaNombre(String texto);

    /**
     * Buscador de funcionarios paginado: id, nombre o nickname del usuario. El nickname es del
     * usuario de la misma persona y no todo funcionario tiene usuario: exists y no join, para no
     * perder a los que no tienen ni duplicar filas en el count de la pagina.
     */
    @Query("select f from Funcionario f " +
            "join f.persona p where " +
            "(:soloActivos = false or f.activo = true) and " +
            "(cast(f.id as text) like concat('%', :texto, '%') or " +
            "upper(p.nombre) like concat('%', :texto, '%') or " +
            "exists (select usr.id from Usuario usr where usr.persona = p and " +
            "upper(usr.nickname) like concat('%', :texto, '%'))) " +
            "order by p.nombre")
    public Page<Funcionario> searchPage(@Param("texto") String texto, @Param("soloActivos") boolean soloActivos,
            Pageable pageable);

}
