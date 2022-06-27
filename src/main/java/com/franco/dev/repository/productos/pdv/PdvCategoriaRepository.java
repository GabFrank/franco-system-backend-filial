package com.franco.dev.repository.productos.pdv;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.pdv.PdvCategoria;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PdvCategoriaRepository extends HelperRepository<PdvCategoria, Long> {

    default Class<PdvCategoria> getEntityClass() {
        return PdvCategoria.class;
    }

    @Query("select f from PdvCategoria f where CAST(id as text) like %?1% or UPPER(descripcion) like %?1%")
    public List<PdvCategoria> findByAll(String texto);

    @Query(value = "select * from productos.pdv_categoria pc " +
            "where pc.activo = true order  by pc.posicion asc;", nativeQuery = true)
    public List<PdvCategoria> findAllSortByPosition();

}
