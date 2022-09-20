package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RetiroDetalleRepository extends HelperRepository<RetiroDetalle, Long> {

    default Class<RetiroDetalle> getEntityClass() {
        return RetiroDetalle.class;
    }

//    @Query("select m from RetiroDetalle m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<RetiroDetalle> findByAll(String texto);

    public List<RetiroDetalle> findByRetiroId(Long id);
    public List<RetiroDetalle> findByRetiroIdAndMonedaId(Long id,Long monedaId);

    public List<RetiroDetalle> findByRetiroCajaSalidaId(Long id);

//    Moneda findByPaisId(Long id);

}