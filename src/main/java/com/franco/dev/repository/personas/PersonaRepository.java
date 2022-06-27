package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PersonaRepository extends HelperRepository<Persona, Long> {

    default Class<Persona> getEntityClass() {
        return Persona.class;
    }

    public Persona findByNombre(String nombre);

    @Query("select p from Persona p where CAST(id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(p.apodo) like %?1% or UPPER(p.documento) like %?1%")
    public List<Persona> findbyAll(String texto);
}
