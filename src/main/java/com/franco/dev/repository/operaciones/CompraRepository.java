package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Compra;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompraRepository extends HelperRepository<Compra, Long> {
    default Class<Compra> getEntityClass() {
        return Compra.class;
    }

//    public Compra findByAlgunaColumna(String texto);

//    public List<Compra> findByProductoId(Long id);

    @Query(value = "select * from operaciones.compra c " +
            "left join operaciones.compra_item ci on c.id = ci.compra_id " +
            "left join productos.producto p on p.id = ci.producto_id " +
            "where p.id = :id and c.estado = 'ACTIVO' order by c.creado_en asc;", nativeQuery = true)
    public List<Compra> findByProductoId(Long id);

    public Compra findByPedidoId(Long id);
}