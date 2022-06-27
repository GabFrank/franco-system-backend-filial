package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CobroDetalleRepository extends HelperRepository<CobroDetalle, Long> {
    default Class<CobroDetalle> getEntityClass() {
        return CobroDetalle.class;
    }

    public List<CobroDetalle> findByCobroId(Long id);

//    @Query("select p from Venta p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Venta> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    @Query(value = "select * from operaciones.cobro_detalle cd " +
            "left join operaciones.cobro c on cd.cobro_id = c.id " +
            "left join operaciones.venta v on v.cobro_id = c.id " +
            "left join financiero.pdv_caja pc on pc.id = v.caja_id " +
            "where v.estado = 'CONCLUIDA' and pc.id = ?1", nativeQuery = true)
    public List<CobroDetalle> findByCajaId(Long id);
}