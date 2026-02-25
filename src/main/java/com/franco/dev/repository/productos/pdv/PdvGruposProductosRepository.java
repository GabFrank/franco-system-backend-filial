package com.franco.dev.repository.productos.pdv;

import com.franco.dev.domain.productos.pdv.PdvGruposProductos;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PdvGruposProductosRepository extends HelperRepository<PdvGruposProductos, Long> {

    default Class<PdvGruposProductos> getEntityClass() {
        return PdvGruposProductos.class;
    }

    @Query("select pg from PdvGruposProductos pg " +
            "left join pg.pdvGrupo as g " +
            "left join pg.producto as p " +
            "where CAST(pg.id as text) like %?1% or UPPER(g.descripcion) like %?1% or UPPER(p.descripcion) like %?1%")
    public List<PdvGruposProductos> findByAll(String texto);

    @Query(value = "select pg.* from productos.pdv_grupos_productos pg " +
            "join productos.producto p on p.id = pg.producto_id " +
            "where pg.grupo_id = ?1 " +
            "and EXISTS (SELECT 1 FROM productos.presentacion pres WHERE pres.producto_id = p.id AND pres.activo = true)", nativeQuery = true)
    public List<PdvGruposProductos> findByPdvGrupoId(Long id);

    public List<PdvGruposProductos> findByProductoId(Long id);

    public List<PdvGruposProductos> findByPdvGrupoIdAndProductoId(Long id1, Long id2);

}
