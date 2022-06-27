package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CuentaBancariaRepository extends HelperRepository<CuentaBancaria, Long> {

    default Class<CuentaBancariaRepository> getEntityClass() {
        return CuentaBancariaRepository.class;
    }

    @Query("select m from CuentaBancaria m " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(numero) like %?1%")
    public List<CuentaBancaria> findByAll(String texto);

//    Moneda findByPaisId(Long id);

}