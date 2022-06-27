package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Documento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocumentoRepository extends HelperRepository<Documento, Long> {

    default Class<Documento> getEntityClass() {
        return Documento.class;
    }

    @Query("select m from Documento m " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
    public List<Documento> findByAll(String texto);

//    Moneda findByPaisId(Long id);

}