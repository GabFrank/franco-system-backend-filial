package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemSucursal;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface PedidoItemSucursalRepository extends HelperRepository<PedidoItemSucursal, Long> {
    default Class<PedidoItemSucursal> getEntityClass() {
        return PedidoItemSucursal.class;
    }

    public List<PedidoItemSucursal> findByPedidoItemId(Long id);

    public List<PedidoItemSucursal> findBySucursalId(Long id);

    public List<PedidoItemSucursal> findBySucursalEntregaId(Long id);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);
}