package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLotePresentacionDto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.operaciones.MovimientoStockLoteService;
import com.franco.dev.service.productos.PresentacionService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Consulta de stock por lote de esta filial.
 *
 * Solo lectura y solo DTOs: la entidad MovimientoStockLote no sale por la API. El descuento por
 * venta no se expone como mutation porque lo dispara la propia venta, no el cliente.
 */
@Component
public class MovimientoStockLoteGraphQL implements GraphQLQueryResolver {

    @Autowired
    private MovimientoStockLoteService service;

    @Autowired
    private PresentacionService presentacionService;

    /** Saldo por lote en unidades base, en orden FEFO. Devuelve todos los estados. */
    public List<StockLoteDto> stockPorLote(Long productoId, Long sucursalId) {
        return service.stockPorLote(productoId, sucursalId);
    }

    /**
     * Saldo por lote convertido a la presentación con la que cobra el cajero. Es lo que alimenta el
     * selector de lotes del POS.
     */
    public List<StockLotePresentacionDto> stockLotePorPresentacion(Long productoId, Long sucursalId,
                                                                   Long presentacionId) {
        if (presentacionId == null) {
            return new ArrayList<>();
        }
        Presentacion presentacion = presentacionService.findById(presentacionId).orElse(null);
        return service.stockLotePorPresentacion(productoId, sucursalId, presentacion);
    }
}
