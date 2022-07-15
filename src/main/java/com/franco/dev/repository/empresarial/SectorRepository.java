package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.Sector;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SectorRepository extends HelperRepository<Sector, Long> {

    default Class<Sector> getEntityClass() {
        return Sector.class;
    }

    @Query("select p from Sector p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1%")
    public List<Sector> findByAll(String texto);

    public List<Sector> findBySucursalIdOrderByIdAsc(Long id);

//    public List<Pais> findBySubFamiliaId(Long id);

}