package com.franco.dev.repository.general;

import com.franco.dev.domain.general.Barrio;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BarrioRepository extends HelperRepository<Barrio, Long> {

    default Class<Barrio> getEntityClass() {
        return Barrio.class;
    }

    public List<Barrio> findByDescripcion(String texto);

    @Query("select p from Barrio p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1%")
    public List<Barrio> findByAll(String texto);

    public List<Barrio> findByCiudadId(Long id);

//    public List<Pais> findBySubFamiliaId(Long id);

}