package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.ProductoImagen;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductoImagenRepository extends HelperRepository<ProductoImagen, Long> {

    default Class<ProductoImagen> getEntityClass() {
        return ProductoImagen.class;
    }

    @Query(value = "select * from productos.producto_imagen pi " +
            "where pi.producto_id = ?1" , nativeQuery = true)
    public List<ProductoImagen> findByProductoId(Long id);

    @Query(value = "select * from productos.producto_imagen pi " +
            "where pi.producto_id = ?1 AND pi.principal = true " +
            "limit 1" , nativeQuery = true)
    public ProductoImagen findImagenPrincipalByProductoId(Long id);

}
