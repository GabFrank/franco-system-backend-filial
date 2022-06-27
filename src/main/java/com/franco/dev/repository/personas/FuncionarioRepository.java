package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FuncionarioRepository extends HelperRepository<Funcionario, Long> {

    default Class<Funcionario> getEntityClass() {
        return Funcionario.class;
    }

    public Funcionario findByPersonaId(Long id);

    @Query("select u from Funcionario u " +
            "join u.persona p " +
            "where CAST(u.id as text) like %?1% or UPPER(p.nombre) like %?1%")
    public List<Funcionario> findByIdOrPersonaNombre(String texto);

}
