package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GastoRepository extends HelperRepository<Gasto, Long> {

    default Class<Gasto> getEntityClass() {
        return Gasto.class;
    }

    @Query(value = "select * from financiero.gasto ms \n" +
            "where ms.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp)", nativeQuery = true)
    public List<Gasto> findByDate(String inicio, String fin);

    public List<Gasto> findByCajaId(Long id);

}