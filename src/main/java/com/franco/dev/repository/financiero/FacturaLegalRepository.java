package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FacturaLegalRepository extends HelperRepository<FacturaLegal, Long> {

    default Class<FacturaLegal> getEntityClass() {
        return FacturaLegal.class;
    }

//    @Query("select m from Moneda m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(denominacion) like %?1%")
//    public List<Moneda> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    public List<FacturaLegal> findByCajaId(Long id);

    public FacturaLegal findByVentaId(Long id);

    /**
     * Candidatas para el aviso de posible factura duplicada: facturas del mismo cajero,
     * al mismo cliente, emitidas desde {@code desde} (el arranque del día). El filtro
     * fino (monto e items) se resuelve después en {@code FacturaSimilarService}.
     * <p>
     * No se acota por caja porque {@code factura_legal.caja_id} casi nunca se persiste:
     * el diálogo de factura no siempre tiene una caja seleccionada.
     */
    public List<FacturaLegal> findByUsuarioIdAndClienteIdAndSucursalIdAndCreadoEnGreaterThanEqualOrderByIdDesc(
            Long usuarioId, Long clienteId, Long sucursalId, LocalDateTime desde);

}