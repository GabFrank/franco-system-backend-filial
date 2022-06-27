package com.franco.dev.repository.restaurant;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.restaurant.PedidoRes;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PedidoResRepository extends HelperRepository<PedidoRes, Long> {

    default Class<PedidoRes> getEntityClass() {
        return PedidoRes.class;
    }

//    public PedidoRes findByDescripcion(String texto);

//    @Query("select p from PedidoRes p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    public List<PedidoRes> findbyAll(String texto);
//
//    public List<PedidoRes> findBySubFamiliaId(Long id);

}
