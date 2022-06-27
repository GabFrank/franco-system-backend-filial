package com.franco.dev.repository.restaurant;

import com.franco.dev.domain.restaurant.PedidoItemRes;
import com.franco.dev.domain.restaurant.PedidoRes;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface PedidoItemResRepository extends HelperRepository<PedidoItemRes, Long> {

    default Class<PedidoItemRes> getEntityClass() {
        return PedidoItemRes.class;
    }

    public List<PedidoItemRes> findByPedidoResId(Long id);

//    @Query("select p from PedidoRes p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    public List<PedidoRes> findbyAll(String texto);
//
//    public List<PedidoRes> findBySubFamiliaId(Long id);

}
