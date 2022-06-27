package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Combo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ComboRepository extends HelperRepository<Combo, Long> {

    default Class<Combo> getEntityClass() {
        return Combo.class;
    }

    @Query("select c from Combo c " +
            "left outer join c.producto as p " +
            "where UPPER(p.descripcion) like %?1%")
    public List<Combo> findByProducto(String texto);

    public Combo findByProductoId(Long id);

}
