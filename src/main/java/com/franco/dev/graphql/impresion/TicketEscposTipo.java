package com.franco.dev.graphql.impresion;

/** Que comprobante del POS genera {@link TicketEscposGraphQL#ticketEscpos}. */
public enum TicketEscposTipo {
    /** La venta: su factura si tiene, si no el ticket. Igual que reimprimirVenta. */
    VENTA,
    /** Una factura legal, normal o en moneda extranjera. Igual que reimprimirFacturaLegal. */
    FACTURA,
    /** El balance de una caja (id = caja). Igual que imprimirBalance. */
    BALANCE,
    /** Un gasto. Igual que reimprimirGasto, con el flag de reimpresion que pida el cliente. */
    GASTO,
    /** Un retiro. Igual que reimprimirRetiro, con el flag de reimpresion que pida el cliente. */
    RETIRO,
    /** El ticket de la venta de un delivery (id = delivery). Igual que reimprimirDelivery. */
    DELIVERY
}
