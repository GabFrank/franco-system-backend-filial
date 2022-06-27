package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VendedorRepository extends HelperRepository<Vendedor, Long> {

    default Class<Vendedor> getEntityClass() {
        return Vendedor.class;
    }

    public Vendedor findByPersonaId(Long id);

    @Query("select distinct v from Vendedor v " +
            "left outer join v.persona as per2 " +
            "where UPPER(per2.nombre) like %?1%")
    public List<Vendedor> findByPersona(String texto);

}
