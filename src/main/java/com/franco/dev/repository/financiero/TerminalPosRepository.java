package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TerminalPosRepository extends HelperRepository<TerminalPos, Long> {

    default Class<TerminalPos> getEntityClass() {
        return TerminalPos.class;
    }

    @Query("select t from TerminalPos t " +
            "where (UPPER(CAST(t.id as text)) like %?1% or UPPER(t.descripcion) like %?1% or UPPER(t.codigo) like %?1%)")
    public List<TerminalPos> findByAll(String texto);

    @Query(value = "select t from TerminalPos t " +
            "where (:descripcion is null or UPPER(t.descripcion) like %:descripcion%) " +
            "and (:codigo is null or UPPER(t.codigo) like %:codigo%) " +
            "and (:activo is null or t.activo = :activo) " +
            "order by t.id asc",
            countQuery = "select count(t) from TerminalPos t " +
                    "where (:descripcion is null or UPPER(t.descripcion) like %:descripcion%) " +
                    "and (:codigo is null or UPPER(t.codigo) like %:codigo%) " +
                    "and (:activo is null or t.activo = :activo)")
    public Page<TerminalPos> filterTerminalPos(@Param("descripcion") String descripcion,
                                               @Param("codigo") String codigo,
                                               @Param("activo") Boolean activo,
                                               Pageable pageable);

    TerminalPos findByCodigoIgnoreCase(String codigo);

    Page<TerminalPos> findAll(Pageable pageable);
}
