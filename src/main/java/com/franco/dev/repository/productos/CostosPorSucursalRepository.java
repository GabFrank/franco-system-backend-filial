package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

public interface CostosPorSucursalRepository extends HelperRepository<CostoPorProducto, Long> {

    default Class<CostoPorProducto> getEntityClass() {
        return CostoPorProducto.class;
    }

//    @Query("select f from CostosPorSucursal f where CAST(id as text) like %?1% or UPPER(descripcion) like %?1%")
//    public List<CostosPorSucursal> findByAll(String texto);

    @Query(
            value = "select * from productos.costo_por_producto cps " +
                    "where cps.producto_id = ?1 " +
                    "order by cps.creado_en desc limit 1",
            nativeQuery = true
    )
    public CostoPorProducto findLastByProductoId(Long id);

    @Query(value = "select * from productos.costo_por_producto cps " +
            "where cps.movimiento_stock_id = ?1",
           nativeQuery = true)
    public CostoPorProducto findByMovimientoStockId(Long id);


//    @Query("select c from CostosPorSucursal c " +
//            "left join c.producto as pro " +
//            "where pro.id = ?1 " +
//            "order by pro.creadoEn desc " +
//            "limit 1")
//    public CostosPorSucursal findLastByProductoId(Long id);

}
