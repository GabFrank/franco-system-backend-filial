package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductoProveedorRepository extends HelperRepository<ProductoProveedor, Long> {

    default Class<ProductoProveedor> getEntityClass() {
        return ProductoProveedor.class;
    }


}
