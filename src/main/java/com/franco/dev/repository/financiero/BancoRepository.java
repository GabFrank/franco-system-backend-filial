package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BancoRepository extends HelperRepository<Banco, Long> {

    default Class<Banco> getEntityClass() {
        return Banco.class;
    }

    @Query("select m from Banco m " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
    public List<Banco> findByAll(String texto);

//    Moneda findByPaisId(Long id);

}