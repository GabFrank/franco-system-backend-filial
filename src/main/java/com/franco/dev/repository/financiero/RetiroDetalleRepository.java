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

    @Query(value = "select rd.id, rd.retiro_id , rd.cambio , rd.cantidad , rd.creado_en , rd.moneda_id , rd.usuario_id from financiero.retiro_detalle rd " +
            "left join financiero.retiro r on r.id = rd.retiro_id " +
            "left join financiero.pdv_caja pc on pc.id = r.caja_salida_id " +
            "where pc.id = ?1", nativeQuery = true)
    public List<RetiroDetalle> findByCajaSalidaId(Long id);

//    Moneda findByPaisId(Long id);

}