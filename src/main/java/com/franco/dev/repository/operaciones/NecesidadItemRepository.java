package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NecesidadItem;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface NecesidadItemRepository extends HelperRepository<NecesidadItem, Long> {
    default Class<NecesidadItem> getEntityClass() {
        return NecesidadItem.class;
    }

    public List<NecesidadItem> findByNecesidadId(Long id);

    public List<NecesidadItem> findByProductoId(Long id);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);
}