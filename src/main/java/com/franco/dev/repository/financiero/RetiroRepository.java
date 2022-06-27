package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RetiroRepository extends HelperRepository<Retiro, Long> {

    default Class<Retiro> getEntityClass() {
        return Retiro.class;
    }

//    @Query("select m from Retiro m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<Retiro> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    public List<Retiro> findByCajaSalidaId(Long id);

}