package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;

public interface PdvCajaRepository extends HelperRepository<PdvCaja, Long> {

    default Class<PdvCaja> getEntityClass() {
        return PdvCaja.class;
    }

    List<PdvCaja> findByUsuarioIdAndActivo(Long id, Boolean activo);

    boolean existsByMaletinIdAndActivoTrueAndIdNot(Long maletinId, Long id);

    /** Hay una caja abierta despues de esta con el mismo maletin (los ids de caja son MAX+1, crecen). */
    boolean existsByMaletinIdAndIdGreaterThan(Long maletinId, Long id);

    Optional<PdvCaja> findById(Long id);

    Optional<PdvCaja> findFirstByConteoAperturaIdOrConteoCierreId(Long conteoAperturaId, Long conteoCierreId);

    @Query("SELECT v.id FROM PdvCaja c LEFT JOIN c.verificadoPor v WHERE c.id = :id")
    Long findVerificadoPorIdById(@Param("id") Long id);

    /**
     * Toma la caja con lock pesimista antes de cargarle un conteo.
     *
     * <p>Sin esto, dos conteos simultaneos sobre la misma caja --doble clic, un reintento del
     * central tras un timeout, el PDV y el admin a la vez-- leen los dos la caja sin conteo, y
     * los dos persisten su conteo con sus movimientos de caja. El que pierde queda huerfano pero
     * sumando efectivo. El SERIALIZABLE de los servicios no lo evita: se unen a la transaccion
     * ya abierta del resolver y su nivel de aislamiento se ignora.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PdvCaja c WHERE c.id = :id")
    Optional<PdvCaja> findByIdForUpdate(@Param("id") Long id);

    public List<PdvCaja> findByCreadoEnBetween(LocalDateTime inicio, LocalDateTime fin);

    Optional<PdvCaja> findFirstByMaletinIdOrderByCreadoEnDesc(Long id);

    @Query(value = "select c from PdvCaja c " +
            "join c.maletin m " +
            "join c.usuario u " +
            "where c.sucursalId = :sucId and " +
            "(:cajaId is null or c.id = :cajaId) and " +
            "(:maletinId is null or m.id = :maletinId) and " +
            "(:cajeroId is null or u.id = :cajeroId) and " +
            "(:verificado is null or c.verificado = :verificado) and " +
            "((:cajaId is not null) or (cast(:fechaInicio as timestamp) is null or cast(:fechaFin as timestamp) is null) or c.creadoEn between :fechaInicio and :fechaFin) and " +
            "(c.estado = :estado or cast(:estado as com.franco.dev.domain.financiero.enums.PdvCajaEstado) is null) order by c.id")
    public Page<PdvCaja> findAllWithFilters(Long cajaId, PdvCajaEstado estado, Long maletinId, Long cajeroId, LocalDateTime fechaInicio, LocalDateTime fechaFin, Long sucId, Boolean verificado, Pageable pageable);

    /**
     * Finds the maximum ID value in the PdvCaja table
     * @return The maximum ID value or null if table is empty
     */
    @Query("SELECT MAX(c.id) FROM PdvCaja c")
    Long findMaxId();


    /**
     * La caja anterior del mismo maletin (fase 2b, eventos): el mismo criterio que el central
     * (findPreviousCajaByMaletinAndFecha), sobre las cajas de esta filial.
     */
    @org.springframework.data.jpa.repository.Query("SELECT c FROM PdvCaja c WHERE c.maletin.id = :maletinId AND c.fechaCierre < :fechaApertura"
            + " AND c.conteoCierre IS NOT NULL AND c.id <> :cajaId ORDER BY c.fechaCierre DESC")
    java.util.List<PdvCaja> findPreviousCajaByMaletinAndFecha(@org.springframework.data.repository.query.Param("maletinId") Long maletinId,
                                                              @org.springframework.data.repository.query.Param("fechaApertura") java.time.LocalDateTime fechaApertura,
                                                              @org.springframework.data.repository.query.Param("cajaId") Long cajaId,
                                                              org.springframework.data.domain.Pageable pageable);

    /**
     * Version liviana para los avisos (fase 2b): id, fecha de cierre, usuario (id, nickname) y conteo de cierre
     * de la caja anterior del mismo maletin, sin cargar entidades.
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT c.id, c.fecha_cierre, u.id AS uid, u.nickname, c.conteo_cierre_id FROM financiero.pdv_caja c"
            + " LEFT JOIN personas.usuario u ON u.id = c.usuario_id WHERE c.maletin_id = :maletinId AND c.fecha_cierre < :fechaApertura"
            + " AND c.conteo_cierre_id IS NOT NULL AND c.id <> :cajaId ORDER BY c.fecha_cierre DESC LIMIT 1", nativeQuery = true)
    java.util.List<Object[]> findCajaAnteriorLiviana(@org.springframework.data.repository.query.Param("maletinId") Long maletinId,
                                                    @org.springframework.data.repository.query.Param("fechaApertura") java.time.LocalDateTime fechaApertura,
                                                    @org.springframework.data.repository.query.Param("cajaId") Long cajaId);

    /** Total contado por moneda (denominacion) en un conteo, en una sola consulta (fase 2b). */
    @org.springframework.data.jpa.repository.Query(value = "SELECT mo.denominacion, SUM(cm.cantidad * mb.valor) FROM financiero.conteo_moneda cm"
            + " JOIN financiero.moneda_billetes mb ON mb.id = cm.moneda_billetes_id JOIN financiero.moneda mo ON mo.id = mb.moneda_id"
            + " WHERE cm.conteo_id = :conteoId AND cm.cantidad IS NOT NULL AND mb.valor IS NOT NULL GROUP BY mo.denominacion", nativeQuery = true)
    java.util.List<Object[]> totalesDeConteo(@org.springframework.data.repository.query.Param("conteoId") Long conteoId);
}
