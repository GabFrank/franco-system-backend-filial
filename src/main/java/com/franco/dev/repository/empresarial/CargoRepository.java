package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CargoRepository extends HelperRepository<Cargo, Long> {

    default Class<Cargo> getEntityClass() {
        return Cargo.class;
    }

    public List<Cargo> findByDescripcion(String texto);

    @Query("select p from Cargo p where CAST(id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(p.descripcion) like %?1%")
    public List<Cargo> findByAll(String texto);

//    public List<Pais> findBySubFamiliaId(Long id);

}