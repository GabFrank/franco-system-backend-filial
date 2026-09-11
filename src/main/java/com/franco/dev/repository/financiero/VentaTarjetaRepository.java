package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

import java.util.List;

@Repository
public interface VentaTarjetaRepository extends HelperRepository<VentaTarjeta, Long> {

    default Class<VentaTarjeta> getEntityClass() {
        return VentaTarjeta.class;
    }

    VentaTarjeta findByIdAndSucursalId(Long id, Long sucursalId);

    VentaTarjeta findFirstByVentaIdAndSucursalIdAndEstadoOrderByIdAsc(Long ventaId, Long sucursalId, String estado);

    VentaTarjeta findFirstByVentaIdAndSucursalIdOrderByIdAsc(Long ventaId, Long sucursalId);

    List<VentaTarjeta> findByVentaIdAndSucursalIdOrderByIdAsc(Long ventaId, Long sucursalId);

    List<VentaTarjeta> findByCajaIdAndSucursalIdOrderByCreadoEnDesc(Long cajaId, Long sucursalId);

    List<VentaTarjeta> findByCajaIdAndSucursalIdAndEstado(Long cajaId, Long sucursalId, String estado);

    Long countByCajaIdAndSucursalIdAndEstado(Long cajaId, Long sucursalId, String estado);

    /** Un cupon solo puede estar registrado una vez: sirve para detectar el re-escaneo. */
    List<VentaTarjeta> findByQrCrudo(String qrCrudo);

    /**
     * Ventas con tarjeta de UNA caja, paginadas y filtradas.
     *
     * `cajaId` y `sucursalId` son obligatorios y no son filtros opcionales: acotan el universo.
     * Es lo que hace que esta consulta no pueda devolver datos de otra caja ni de otra sucursal
     * aunque el cliente mande cualquier cosa en el resto de los parametros.
     *
     * El orden es descendente por fecha: lo ultimo cobrado es lo que el cajero esta buscando.
     */
    @Query("SELECT vt FROM VentaTarjeta vt " +
            "WHERE vt.cajaId = :cajaId " +
            "AND vt.sucursalId = :sucursalId " +
            "AND (:estado IS NULL OR vt.estado = :estado) " +
            "AND (:terminalPosId IS NULL OR vt.terminalPos.id = :terminalPosId) " +
            "AND (:monedaId IS NULL OR vt.moneda.id = :monedaId) " +
            "AND (:montoDesde IS NULL OR vt.monto >= :montoDesde) " +
            "AND (:montoHasta IS NULL OR vt.monto <= :montoHasta) " +
            "ORDER BY vt.creadoEn DESC")
    Page<VentaTarjeta> filtrarPorCaja(
            @Param("cajaId") Long cajaId,
            @Param("sucursalId") Long sucursalId,
            @Param("estado") String estado,
            @Param("terminalPosId") Long terminalPosId,
            @Param("monedaId") Long monedaId,
            @Param("montoDesde") BigDecimal montoDesde,
            @Param("montoHasta") BigDecimal montoHasta,
            Pageable pageable);
}
