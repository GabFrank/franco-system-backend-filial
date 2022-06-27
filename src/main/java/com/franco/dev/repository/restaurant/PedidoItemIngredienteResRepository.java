package com.franco.dev.repository.restaurant;

import com.franco.dev.domain.restaurant.PedidoItemIngredienteRes;
import com.franco.dev.domain.restaurant.PedidoItemRes;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface PedidoItemIngredienteResRepository extends HelperRepository<PedidoItemIngredienteRes, Long> {

    default Class<PedidoItemIngredienteRes> getEntityClass() {
        return PedidoItemIngredienteRes.class;
    }

    public List<PedidoItemIngredienteRes> findByPedidoItemResId(Long id);

//    @Query("select p from PedidoRes p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    public List<PedidoRes> findbyAll(String texto);
//
//    public List<PedidoRes> findBySubFamiliaId(Long id);

}
