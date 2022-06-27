package com.franco.dev.repository.general;

import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CiudadRepository extends HelperRepository<Ciudad, Long> {

    default Class<Ciudad> getEntityClass() {
        return Ciudad.class;
    }

    public List<Ciudad> findByDescripcion(String texto);

    @Query("select p from Ciudad p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.codigo) like %?1%")
    public List<Ciudad> findByAll(String texto);

//    public List<Pais> findBySubFamiliaId(Long id);

}