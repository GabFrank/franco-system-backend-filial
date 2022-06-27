package com.franco.dev.repository.general;

import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaisRepository extends HelperRepository<Pais, Long> {

    default Class<Pais> getEntityClass() {
        return Pais.class;
    }

    public List<Pais> findByDescripcion(String texto);

    @Query("select p from Pais p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.codigo) like %?1%")
    public List<Pais> findbyAll(String texto);

//    public List<Pais> findBySubFamiliaId(Long id);

}