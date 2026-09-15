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

    /**
     * Mismo filtro que el del central, con los mismos parametros.
     * <p>
     * <b>Los dos schemas tienen que aceptar los mismos argumentos</b>, aunque el ABM viva solo en
     * central: el desktop usa UNA sola query para los dos backends, y GraphQL valida el documento
     * entero contra el schema. Si el filial no declarara `serie` y `sucursalId`, la consulta se
     * rechazaria completa y el cajero no podria ni elegir la terminal en el PDV.
     */
    @Query(value = "select t from TerminalPos t " +
            "where (:descripcion is null or UPPER(t.descripcion) like %:descripcion%) " +
            "and (:codigo is null or UPPER(t.codigo) like %:codigo%) " +
            "and (:serie is null or UPPER(t.serie) like %:serie%) " +
            "and (:sucursalId is null or t.sucursalId = :sucursalId) " +
            "and (:activo is null or t.activo = :activo) " +
            "order by t.id asc",
            countQuery = "select count(t) from TerminalPos t " +
                    "where (:descripcion is null or UPPER(t.descripcion) like %:descripcion%) " +
                    "and (:codigo is null or UPPER(t.codigo) like %:codigo%) " +
                    "and (:serie is null or UPPER(t.serie) like %:serie%) " +
                    "and (:sucursalId is null or t.sucursalId = :sucursalId) " +
                    "and (:activo is null or t.activo = :activo)")
    public Page<TerminalPos> filterTerminalPos(@Param("descripcion") String descripcion,
                                               @Param("codigo") String codigo,
                                               @Param("serie") String serie,
                                               @Param("sucursalId") Long sucursalId,
                                               @Param("activo") Boolean activo,
                                               Pageable pageable);

    TerminalPos findByCodigoIgnoreCase(String codigo);

    /**
     * Las terminales activas con EXACTAMENTE esta serie, para resolver de que aparato salio un
     * cupon. Exacta y no LIKE: el valor viene del propio cupon y quien llama lo acepta sin
     * preguntar cuando hay uno solo, asi que un `%` ahi adentro seria un comodin de SQL.
     */
    List<TerminalPos> findBySerieIgnoreCaseAndActivoTrue(String serie);

    Page<TerminalPos> findAll(Pageable pageable);
}
