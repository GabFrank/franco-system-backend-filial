package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Ingrediente;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IngredienteRepository extends HelperRepository<Ingrediente, Long> {

    default Class<Ingrediente> getEntityClass() {
        return Ingrediente.class;
    }

    public List<Ingrediente> findByDescripcion(String texto);

//    @Query("select p from Producto p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.descripcionFactura) like %?1%")
//    public List<Producto> findbyAll(String texto);

//    public List<Producto> findBySubFamiliaId(Long id);
}
