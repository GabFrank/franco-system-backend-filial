package com.franco.dev.service.financiero.builder;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.TimbradoDetalle;

import java.util.List;

/**
 * Input para FacturaLegalBuilder.build(). Contiene la entidad FacturaLegal
 * pre-armada (cliente, ruc, nombre, direccion, descuento, moneda, credito,
 * sucursalId, etc.) + la lista de descriptores de items + metadata del flow.
 *
 * Para resolver el timbrado: pasar pdvId (el builder hace lookup) o
 * timbradoDetalle pre-resuelto (caller ya tiene la entidad).
 */
public class BuildRequest {

    private final FacturaLegal facturaLegal;
    private final List<FacturaLegalItemDescriptor> items;
    private final Long pdvId;
    private final TimbradoDetalle timbradoDetalle;
    private final boolean generarDE;

    public BuildRequest(FacturaLegal facturaLegal,
                        List<FacturaLegalItemDescriptor> items,
                        Long pdvId,
                        boolean generarDE) {
        this(facturaLegal, items, pdvId, null, generarDE);
    }

    public BuildRequest(FacturaLegal facturaLegal,
                        List<FacturaLegalItemDescriptor> items,
                        TimbradoDetalle timbradoDetalle,
                        boolean generarDE) {
        this(facturaLegal, items, null, timbradoDetalle, generarDE);
    }

    private BuildRequest(FacturaLegal facturaLegal,
                         List<FacturaLegalItemDescriptor> items,
                         Long pdvId,
                         TimbradoDetalle timbradoDetalle,
                         boolean generarDE) {
        this.facturaLegal = facturaLegal;
        this.items = items;
        this.pdvId = pdvId;
        this.timbradoDetalle = timbradoDetalle;
        this.generarDE = generarDE;
    }

    public FacturaLegal getFacturaLegal() { return facturaLegal; }
    public List<FacturaLegalItemDescriptor> getItems() { return items; }
    public Long getPdvId() { return pdvId; }
    public TimbradoDetalle getTimbradoDetalle() { return timbradoDetalle; }
    public boolean isGenerarDE() { return generarDE; }
}
