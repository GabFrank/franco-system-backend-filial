package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeliveryRepository extends HelperRepository<Delivery, Long> {
    default Class<Delivery> getEntityClass() {
        return Delivery.class;
    }

    public List<Delivery> findByEstado(DeliveryEstado estado);

    @Query(value = "select * from operaciones.delivery d " +
            "where cast(d.estado as text) = 'ABIERTO' or cast(d.estado as text) = 'EN_CAMINO'", nativeQuery = true)
    public List<Delivery> findActivos();

    @Query(value = "select * from operaciones.delivery d " +
            "where cast(d.estado as text) != 'ABIERTO' and cast(d.estado as text) != 'EN_CAMINO' limit 10", nativeQuery = true)
    public List<Delivery> findUltimos10();

//    @Query("select p from Delivery p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Delivery> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);
}