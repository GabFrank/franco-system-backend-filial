package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Combo;
import com.franco.dev.domain.productos.ProductoIngrediente;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductoIngredienteRepository extends HelperRepository<ProductoIngrediente, Long> {

    default Class<ProductoIngrediente> getEntityClass() {
        return ProductoIngrediente.class;
    }

    @Query("select c from ProductoIngrediente c " +
            "left outer join c.producto as p " +
            "left outer join c.ingrediente as i " +
            "where UPPER(p.descripcion) like %?1% " +
            "OR UPPER(i.descripcion) like %?1%")
    public List<ProductoIngrediente> findByAll(String texto);

    public List<ProductoIngrediente> findByProductoId(Long id);

    public List<ProductoIngrediente> findByIngredienteId(Long id);

}
