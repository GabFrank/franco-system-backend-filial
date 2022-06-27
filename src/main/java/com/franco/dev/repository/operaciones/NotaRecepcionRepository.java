package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface NotaRecepcionRepository extends HelperRepository<NotaRecepcion, Long> {
    default Class<NotaRecepcion> getEntityClass() {
        return NotaRecepcion.class;
    }

    public List<NotaRecepcion> findByPedidoId(Long id);
//
//    @Query("select p from Pedido p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Pedido> findByProveedor(String texto);
//
//    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    //public List<Producto> findbyAll(String texto);
}