package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InventarioProductoItemRepository extends HelperRepository<InventarioProductoItem, Long> {

    default Class<InventarioProductoItem> getEntityClass() {
        return InventarioProductoItem.class;
    }

    public List<InventarioProductoItem> findByInventarioProductoId(Long id);

    @Query(value = "select * from operaciones.inventario_producto_item ipi " +
            "join productos.presentacion p on p.id = ipi.presentacion_id " +
            "join productos.producto pro on p.producto_id = pro.id " +
            "where pro.id = ?1", nativeQuery = true)
    public List<InventarioProductoItem> findByProductoId(Long id);

}