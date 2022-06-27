package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.domain.personas.VendedorProveedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VendedorProveedorRepository extends HelperRepository<VendedorProveedor, Long> {

    default Class<VendedorProveedor> getEntityClass() {
        return VendedorProveedor.class;
    }

    public List<VendedorProveedor> findDistinctByProveedorId(Long id);

    public List<VendedorProveedor> findDistinctByVendedorId(Long id);

}
