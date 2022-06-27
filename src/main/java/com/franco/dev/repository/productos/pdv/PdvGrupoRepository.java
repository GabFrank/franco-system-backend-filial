package com.franco.dev.repository.productos.pdv;

import com.franco.dev.domain.productos.pdv.PdvCategoria;
import com.franco.dev.domain.productos.pdv.PdvGrupo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PdvGrupoRepository extends HelperRepository<PdvGrupo, Long> {

    default Class<PdvGrupo> getEntityClass() {
        return PdvGrupo.class;
    }

    @Query("select f from PdvGrupo f where CAST(id as text) like %?1% or UPPER(descripcion) like %?1%")
    public List<PdvGrupo> findByAll(String texto);

    public List<PdvGrupo> findByPdvCategoriaId(Long id);

}
