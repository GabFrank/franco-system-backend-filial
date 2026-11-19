package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Necesidad;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NecesidadRepository extends HelperRepository<Necesidad, Long> {
    default Class<Necesidad> getEntityClass() {
        return Necesidad.class;
    }

//    public List<SolicitudCompra> findByProveedorPersonaNombreContainingIgnoreCase(String texto);

//    @Query("select p from SolicitudCompra p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<SolicitudCompra> findByProveedor(String texto);

    @Query("select e from Necesidad e " +
            "left join e.sucursal s " +
            " where CAST(e.id as text) like %?1% or UPPER(s.nombre) like %?1%")
    public List<Necesidad> findByAll(String texto);

}