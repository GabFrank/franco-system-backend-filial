package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.ProductoPorSucursal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductoPorSucursalRepository extends HelperRepository<ProductoPorSucursal, Long> {

    default Class<ProductoPorSucursal> getEntityClass() {
        return ProductoPorSucursal.class;
    }

//    @Query("select c from ProductoPorSucursal c " +
//            "left outer join c.productoPorSucursalProducto as p " +
//            "where LOWER(p.descripcion) like %?1%")
//    public List<ProductoPorSucursal> findByProducto(String texto);

    public List<ProductoPorSucursal> findByProductoId(Long id);
    public List<ProductoPorSucursal> findBySucursalId(Long id);

    @Query(value = "select * from productos.producto_por_sucursal pps " +
            "where pps.producto_id = ?1 AND pps.sucursal_id = ?2 limit 1", nativeQuery = true)
    public ProductoPorSucursal findByProductoIdAndSucursalId(Long productoId, Long sucursalId);

}
