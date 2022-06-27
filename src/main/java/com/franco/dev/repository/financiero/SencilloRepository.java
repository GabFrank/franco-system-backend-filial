package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Sencillo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SencilloRepository extends HelperRepository<Sencillo, Long> {

    default Class<Sencillo> getEntityClass() {
        return Sencillo.class;
    }

//    @Query("select m from Sencillo m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<Sencillo> findByAll(String texto);

//    Moneda findByPaisId(Long id);

}