package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.dto.StockPorTipoMovimientoDto;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.dto.MovimientoStockCantidadAndIdDto;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoStockRepository extends HelperRepository<MovimientoStock, Long> {
    default Class<MovimientoStock> getEntityClass() {
        return MovimientoStock.class;
    }

    public List<MovimientoStock> findByProductoId(String texto);

    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "where p.estado = true and pro.id = ?1")
    public Float stockByProductoId(Long proId);

    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "where p.estado = true and pro.id = ?1 and p.sucursalId = ?2")
    public Float stockByProductoIdAndSucursalId(Long proId, Long sucId);

    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "where p.estado = true and pro.id = ?1 and p.sucursalId = ?2 and p.creadoEn < ?3")
    public Float stockByProductoIdAndSucursalIdAntesDeFecha(Long proId, Long sucId, LocalDateTime fecha);

    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "where p.estado = true and pro.id = ?1 and p.id != ?2 and p.sucursalId = ?3")
    public Float stockByProductoIdExeptMovimientoId(Long proId, Long movId, Long sucId);

    @Query(value = "select * from operaciones.movimiento_stock p " +
            "left join productos.producto as pro on p.producto_id = pro.id " +
            "where pro.id = ?1 " +
            "and cast(p.tipo_movimiento as text) = ?2 " +
            "order by p.creado_en desc limit ?3",
            nativeQuery = true)
    public List<MovimientoStock> ultimosMovimientosPorProductoId(Long id, String tm, Integer size);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    @Query(value = "select * from operaciones.movimiento_stock ms \n" +
            "where ms.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp)", nativeQuery = true)
    public List<MovimientoStock> findByDate(String inicio, String fin);

    public MovimientoStock findByTipoMovimientoAndReferencia(TipoMovimiento tipoMovimiento, Long referencia);

    public MovimientoStock findByProductoIdAndTipoMovimientoAndReferencia(Long proId, TipoMovimiento tipoMovimiento, Long refId);

    public List<MovimientoStock> findByReferencia(Long id);

    @Query("select new com.franco.dev.domain.operaciones.dto.MovimientoStockCantidadAndIdDto(COALESCE(SUM(p.cantidad), 0), MAX(p.id), count(p.id)) " +
            "from MovimientoStock p " +
            "left join p.producto pro " +
            "where p.estado = true and pro.id = ?1 and p.sucursalId = ?2 and p.id > ?3")
    public MovimientoStockCantidadAndIdDto stockByProductoIdAndSucursalIdAndLastId(Long proId, Long sucId, Long lastId);

    @Query(value = "select ms from MovimientoStock ms " +
            "join ms.producto p " +
            "left join ms.usuario u " +
            "where " +
            "((:usuarioId) is null or u.id = (:usuarioId) ) and " +
            "((:sucursalList) is null or ms.sucursalId in (:sucursalList) ) and " +
            "((:productoId) is null or p.id = (:productoId)) and " +
            "((:tipoMovimientoList) IS NULL OR cast(ms.tipoMovimiento as text) IN :tipoMovimientoList) and " +
            "ms.creadoEn between cast((:inicio) as timestamp) and cast((:fin) as timestamp) " +
            "order by ms.creadoEn")
    public Page<MovimientoStock> findByFilters(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalList") List<Long> sucursalList,
            @Param("productoId") Long productoId,
            @Param("tipoMovimientoList") List<String> tipoMovimientoList,
            @Param("usuarioId") Long usuarioId,
            Pageable pageable);

    @Query(value = "select sum(ms.cantidad) from MovimientoStock ms " +
            "join ms.producto p " +
            "left join ms.usuario u " +
            "where " +
            "((:usuarioId) is null or u.id = (:usuarioId) ) and " +
            "((:sucursalList) is null or ms.sucursalId in (:sucursalList) ) and " +
            "((:productoId) is null or p.id = (:productoId)) and " +
            "((:tipoMovimientoList) IS NULL OR cast(ms.tipoMovimiento as text) IN :tipoMovimientoList) and " +
            "(cast(:inicio as timestamp) IS NULL OR ms.creadoEn between cast((:inicio) as timestamp) and cast((:fin) as timestamp))")
    public Double findStockWithFilters(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalList") List<Long> sucursalList,
            @Param("productoId") Long productoId,
            @Param("tipoMovimientoList") List<String> tipoMovimientoList,
            @Param("usuarioId") Long usuarioId);

    @Query(value = "SELECT new com.franco.dev.domain.dto.StockPorTipoMovimientoDto(ms.tipoMovimiento, SUM(ms.cantidad)) " +
            "FROM MovimientoStock ms " +
            "join ms.producto p " +
            "left join ms.usuario u " +
            "where " +
            "((:usuarioId) is null or u.id = (:usuarioId) ) and " +
            "((:sucursalList) is null or ms.sucursalId in (:sucursalList) ) and " +
            "((:productoId) is null or p.id = (:productoId)) and " +
            "((:tipoMovimientoList) IS NULL OR cast(ms.tipoMovimiento as text) IN :tipoMovimientoList) and " +
            "ms.creadoEn between cast((:inicio) as timestamp) and cast((:fin) as timestamp) and ms.estado = true " +
            "group by ms.tipoMovimiento")
    public List<StockPorTipoMovimientoDto> findStockPorTipoMovimiento(@Param("inicio") LocalDateTime inicio,
                                                                      @Param("fin") LocalDateTime fin,
                                                                      @Param("sucursalList") List<Long> sucursalList,
                                                                      @Param("productoId") Long productoId,
                                                                      @Param("tipoMovimientoList") List<String> tipoMovimientoList,
                                                                      @Param("usuarioId") Long usuarioId);

}