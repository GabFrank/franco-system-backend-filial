package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.MotivoDiferenciaPedido;
import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.repository.HelperRepository;

public interface MotivoDiferenciaPedidoRepository extends HelperRepository<MotivoDiferenciaPedido, Long> {
    default Class<MotivoDiferenciaPedido> getEntityClass() {
        return MotivoDiferenciaPedido.class;
    }

//    public List<Pedido> findByProveedorPersonaNombreContainingIgnoreCase(String texto);
//
//    @Query("select p from Pedido p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Pedido> findByProveedor(String texto);
//
//    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    //public List<Producto> findbyAll(String texto);
}