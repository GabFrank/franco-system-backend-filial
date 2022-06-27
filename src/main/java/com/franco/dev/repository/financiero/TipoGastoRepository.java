package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TipoGastoRepository extends HelperRepository<TipoGasto, Long> {

    default Class<TipoGasto> getEntityClass() {
        return TipoGasto.class;
    }

    @Query(value = "select * from financiero.tipo_gasto tg " +
            "where tg.clasificacion_gasto_id is null order by tg.id asc", nativeQuery = true)
    public List<TipoGasto> findRoot();

    @Query(value = "select * from financiero.tipo_gasto tg " +
            "where CAST(tg.id as text) like %?1% or upper(tg.descripcion) like %?1%", nativeQuery = true)
    public List<TipoGasto> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    List<TipoGasto> findByClasificacionGastoId(Long id);

}