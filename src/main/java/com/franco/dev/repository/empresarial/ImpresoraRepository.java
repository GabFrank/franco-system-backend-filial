package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface ImpresoraRepository extends HelperRepository<Impresora, Long> {

    default Class<Impresora> getEntityClass() {
        return Impresora.class;
    }

    List<Impresora> findBySucursalId(Long id);

    List<Impresora> findByActivoTrueOrderByIdAsc();
}
