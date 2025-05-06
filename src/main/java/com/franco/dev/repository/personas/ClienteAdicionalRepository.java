package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.ClienteAdicional;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface ClienteAdicionalRepository extends HelperRepository<ClienteAdicional, Long> {

    default Class<ClienteAdicional> getEntityClass() {
        return ClienteAdicional.class;
    }

    List<ClienteAdicional> findByPersonaId(Long id);

    List<ClienteAdicional> findByClienteId(Long id);
//
//    @Query("select c from Cliente c " +
//            "left join c.persona per " +
//            " where CAST(per.id as text) like %?1% or UPPER(per.nombre) like %?1% or UPPER(per.apodo) like %?1% or UPPER(per.documento) like %?1%")
//    List<Cliente> findByPersona(String texto);
}
