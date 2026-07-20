package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TipoGastoRepository extends HelperRepository<TipoGasto, Long> {

    default Class<TipoGasto> getEntityClass() {
        return TipoGasto.class;
    }

    @Query(value = "select * from financiero.tipo_gasto tg " +
            "where tg.activo = true " +
            "and tg.activo_en_sucursales = true " +
            "and (CAST(tg.id as text) like %?1% or upper(tg.descripcion) like %?1%) " +
            "order by tg.id asc", nativeQuery = true)
    public List<TipoGasto> findByAll(String texto);

    @Query(value = "SELECT tg.* FROM financiero.tipo_gasto tg " +
            "WHERE (CAST(:naturaleza AS text) IS NULL OR cast(tg.tipo_naturaleza as text) = CAST(:naturaleza AS text)) " +
            "AND (CAST(:moduloPadre AS text) IS NULL OR cast(tg.modulo_padre as text) = CAST(:moduloPadre AS text)) " +
            "AND (CAST(:texto AS text) IS NULL OR UPPER(tg.descripcion) LIKE CONCAT('%', UPPER(CAST(:texto AS text)), '%') " +
            "OR CAST(tg.id AS text) LIKE CONCAT('%', CAST(:texto AS text), '%')) " +
            "ORDER BY tg.id ASC",
            countQuery = "SELECT count(*) FROM financiero.tipo_gasto tg " +
            "WHERE (CAST(:naturaleza AS text) IS NULL OR cast(tg.tipo_naturaleza as text) = CAST(:naturaleza AS text)) " +
            "AND (CAST(:moduloPadre AS text) IS NULL OR cast(tg.modulo_padre as text) = CAST(:moduloPadre AS text)) " +
            "AND (CAST(:texto AS text) IS NULL OR UPPER(tg.descripcion) LIKE CONCAT('%', UPPER(CAST(:texto AS text)), '%') " +
            "OR CAST(tg.id AS text) LIKE CONCAT('%', CAST(:texto AS text), '%'))",
            nativeQuery = true)
    Page<TipoGasto> filterTipoGastos(@Param("naturaleza") String naturaleza,
                                    @Param("texto") String texto,
                                    @Param("moduloPadre") String moduloPadre,
                                    Pageable pageable);

}