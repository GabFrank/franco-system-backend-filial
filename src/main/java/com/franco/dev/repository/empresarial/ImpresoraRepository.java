package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.repository.HelperRepository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface ImpresoraRepository extends HelperRepository<Impresora, Long> {

    default Class<Impresora> getEntityClass() {
        return Impresora.class;
    }

    List<Impresora> findBySucursalId(Long id);

    @Query("select i from Impresora i left join i.sucursal s where " +
            "(CAST(i.id as text) like concat('%', ?1, '%') or " +
            "UPPER(i.nombre) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(s.nombre) like UPPER(concat('%', ?1, '%'))) " +
            "order by i.id desc")
    Page<Impresora> findByAllWithPage(String texto, Pageable pageable);

    List<Impresora> findByActivoTrueOrderByIdAsc();
}
