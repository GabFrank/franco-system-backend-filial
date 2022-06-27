package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProveedorRepository extends HelperRepository<Proveedor, Long> {

    default Class<Proveedor> getEntityClass() {
        return Proveedor.class;
    }

    Proveedor findByPersonaId(Long id);

    @Query("select distinct p from Proveedor p " +
            "left outer join p.persona as per " +
            "where UPPER(per.nombre) like %?1% or cast(p.id as text) like %?1%")
    public List<Proveedor> findByPersona(String texto);

    @Query("select distinct pro FROM VendedorProveedor v " +
            "left outer JOIN v.vendedor as ven " +
            "left outer JOIN v.proveedor as pro " +
            "where ven.id = ?1")
    public List<Proveedor> findByVendedorId(Long id);

}
