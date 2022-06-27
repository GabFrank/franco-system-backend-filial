package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.Sector;
import com.franco.dev.domain.empresarial.Zona;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ZonaRepository extends HelperRepository<Zona, Long> {

    default Class<Zona> getEntityClass() {
        return Zona.class;
    }

    @Query("select p from Zona p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1%")
    public List<Zona> findByAll(String texto);

    public List<Zona> findBySectorId(Long id);

//    public List<Pais> findBySubFamiliaId(Long id);

}