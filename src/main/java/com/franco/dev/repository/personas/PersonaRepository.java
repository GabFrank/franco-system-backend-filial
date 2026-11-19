package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PersonaRepository extends HelperRepository<Persona, Long> {

    default Class<Persona> getEntityClass() {
        return Persona.class;
    }

    public Persona findByNombre(String nombre);
    public Persona findByDocumentoIgnoreCase(String documento);
    public Persona findByDocumento(String documento);

    @Query("select p from Persona p where CAST(id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(p.apodo) like %?1% or UPPER(p.documento) like %?1%")
    public List<Persona> findbyAll(String texto);

    @Query("select p from Persona p where CAST(id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(p.apodo) like %?1% or UPPER(p.documento) like %?1%")
    public Page<Persona> findbyAll(String texto, Pageable pageable);

    /** Solo la columna local pais_id (#134): el resto de la persona se edita en el central. */
    @Modifying
    @Transactional
    @Query(value = "update personas.persona set pais_id = :paisId where id = :id", nativeQuery = true)
    int actualizarPais(@Param("id") Long id, @Param("paisId") Long paisId);
}
