package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ClienteRepository extends HelperRepository<Cliente, Long> {

    default Class<Cliente> getEntityClass() {
        return Cliente.class;
    }

    Cliente findByPersonaId(Long id);

    @Query("select c from Cliente c " +
            "left join c.persona per " +
            " where CAST(per.id as text) like %?1% or UPPER(per.nombre) like %?1% or UPPER(per.apodo) like %?1%")
    List<Cliente> findByPersona(String texto);
}
