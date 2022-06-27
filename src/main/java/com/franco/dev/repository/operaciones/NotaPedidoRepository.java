package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotaPedidoRepository extends HelperRepository<NotaPedido, Long> {
    default Class<NotaPedido> getEntityClass() {
        return NotaPedido.class;
    }

//    public List<Pedido> findByProveedorPersonaNombreContainingIgnoreCase(String texto);
//
//    @Query("select p from Pedido p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Pedido> findByProveedor(String texto);
//
//    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    //public List<Producto> findbyAll(String texto);
}