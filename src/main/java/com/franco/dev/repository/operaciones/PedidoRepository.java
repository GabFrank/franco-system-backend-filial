package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoRepository extends HelperRepository<Pedido, Long> {
    default Class<Pedido> getEntityClass() {
        return Pedido.class;
    }
    @Query(value = "select  " +
            "distinct p.id,  " +
            "p.moneda_id, " +
            "p.proveedor_id,  " +
            "p.vendedor_id,  " +
            "p.forma_pago,  " +
            "p.dias_cheque,  " +
            "p.fecha_de_entrega,  " +
            "p.usuario_id, " +
            "p.cantidad_notas, " +
            "p.descuento , " +
            "p.estado , " +
            "p.creado_en , " +
            "p.necesidad_id  " +
            "from operaciones.pedido p " +
            "join operaciones.pedido_item pi2 on pi2.pedido_id = p.id " +
            "join operaciones.pedido_item_sucursal pis on pis.pedido_item_id = pi2.id " +
            "where (cast(p.estado as text) = cast(:estado as text) or cast(:estado as text) is null)  " +
            "and " +
            "(:sucursalId is null or cast(pis.sucursal_id as text) = cast(:sucursalId as text)) " +
            "and " +
            "((:inicio is null) or (p.creado_en between cast(coalesce(:inicio, null) as Date) and cast(coalesce(:fin, null) as Date))) " +
            "and  " +
            "(:proveedorId is null or cast(p.proveedor_id as text) = cast(:proveedorId as text)) " +
            "and  " +
            "(:vendedorId is null or cast(p.vendedor_id as text)= cast(:vendedorId as text)) " +
            "and  " +
            "(cast(:formaPago as text) is null or cast(p.forma_pago as text) = cast(:formaPago as text)) " +
            "and  " +
            "(:productoId is null or cast(pi2.producto_id as text) = cast(:productoId as text)) " +
            "order by p.creado_en desc", nativeQuery = true)
    public List<Pedido> filterPedidos(@Param("estado") String estado, @Param("sucursalId") Long sucursalId, @Param("inicio") String inicio, @Param("fin") String fin, @Param("proveedorId") Long proveedorId, @Param("vendedorId") Long vendedorId, @Param("formaPago") String formaPago, @Param("productoId") Long productoId);
}