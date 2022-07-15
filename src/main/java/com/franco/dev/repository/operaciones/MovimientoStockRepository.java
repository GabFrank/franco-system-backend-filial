package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MovimientoStockRepository extends HelperRepository<MovimientoStock, Long> {
    default Class<MovimientoStock> getEntityClass() {
        return MovimientoStock.class;
    }

    public List<MovimientoStock> findByProductoId(String texto);

    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "left outer join p.sucursal as suc " +
            "where p.estado = true and pro.id = ?1 and suc.id = ?2")
    public Float stockByProductoIdAndSucursalId(Long proId, Long sucId);

    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "where p.estado = true and pro.id = ?1")
    public Float stockByProductoId(Long proId);

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

    @Query(value = "select * from operaciones.movimiento_stock ms " +
            "where ms.referencia = ?1", nativeQuery = true)
    public List<MovimientoStock> findByReferencia(Long id);

}