package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PrecioPorSucursalRepository extends HelperRepository<PrecioPorSucursal, Long> {

    default Class<PrecioPorSucursal> getEntityClass() {
        return PrecioPorSucursal.class;
    }

    public List<PrecioPorSucursal> findByPresentacionId(Long id);
    public List<PrecioPorSucursal> findBySucursalId(Long id);

    @Query(value = "select * from productos.presentacion p " +
            "            left outer join productos.precio_por_sucursal c on c.presentacion_id = p.id " +
            "            where c.principal = true and p.id = ?1 limit 1", nativeQuery = true)
    public PrecioPorSucursal findPrincipalByPresentacionId(Long id);

}
