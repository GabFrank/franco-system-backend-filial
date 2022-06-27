package com.franco.dev.repository.personas;

import com.franco.dev.domain.general.enums.DiasSemana;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.ProveedorDiasVisita;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProveedorDiasVisitaRepository extends HelperRepository<ProveedorDiasVisita, Long> {

    default Class<ProveedorDiasVisita> getEntityClass() {
        return ProveedorDiasVisita.class;
    }

    List<ProveedorDiasVisita> findByProveedorId(Long id);
    List<ProveedorDiasVisita> findByDia(DiasSemana dia);

}
