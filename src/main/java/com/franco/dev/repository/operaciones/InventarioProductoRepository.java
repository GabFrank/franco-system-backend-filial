package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InventarioProductoRepository extends HelperRepository<InventarioProducto, Long> {

    default Class<InventarioProducto> getEntityClass() {
        return InventarioProducto.class;
    }

    public List<InventarioProducto> findByInventarioId(Long id);

    @Query(value = "select * from operaciones.inventario_producto ip " +
            "where ip.inventario_id = ?1 and ip.usuario_id = ?2 and ip.concluido = false", nativeQuery = true)
    public List<InventarioProducto> verificarUsuarioZona(Long invId, Long usuId);


}