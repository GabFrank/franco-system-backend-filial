package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PdvCajaRepository extends HelperRepository<PdvCaja, Long> {

    default Class<PdvCaja> getEntityClass() {
        return PdvCaja.class;
    }

    @Query(value = "select * from financiero.pdv_caja ms \n" +
            "where ms.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp) order by ms.id desc", nativeQuery = true)
    public List<PdvCaja> findByDate(String inicio, String fin);

//    @Query(value = "select * from financiero.pdv_caja pc \n" +
//            "where \n" +
//            "(:maletin_id is null or pc.maletin_id = :maletin_id) and \n" +
//            "(:cajero_id is null or pc.usuario_id = :cajero_id) and \n" +
//            "((:fecha_inicio is null and :fecha_fin is null) or pc.creado_en between cast(:fecha_inicio as timestamp) and cast(:fecha_fin as timestamp))\n")
//    public List<PdvCaja> findByAll();

    public List<PdvCaja> findByUsuarioIdAndActivo(Long id, Boolean activo);

}