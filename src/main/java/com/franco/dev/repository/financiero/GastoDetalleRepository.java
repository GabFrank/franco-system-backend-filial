package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.GastoDetalle;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GastoDetalleRepository extends HelperRepository<GastoDetalle, Long> {

    default Class<GastoDetalle> getEntityClass() {
        return GastoDetalle.class;
    }

//    @Query("select m from GastoDetalle m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<GastoDetalle> findByAll(String texto);

    public List<GastoDetalle> findByGastoId(Long id);

//    Moneda findByPaisId(Long id);

}