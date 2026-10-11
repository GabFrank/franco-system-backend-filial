package com.franco.dev.service.financiero.builder;

import graphql.GraphQLException;

/**
 * La factura no se puede emitir por algo que el cajero corrige en el momento (hoy: una venta sin
 * nombre de 7.000.000 o mas, que se resuelve cargando el cliente). A diferencia del resto de las
 * validaciones del builder, que dejan la venta guardada sin factura, esta RECHAZA la venta: el
 * mensaje llega al cajero, el carrito queda como estaba y se reintenta (issue #170).
 * <p>
 * Es una GraphQLException: sale del builder antes del primer write y sin marcar la transaccion, y
 * cuenta como falla previa a escribir para devolver el turno del contador. Quien la ataja para
 * seguir sin factura tiene que relanzarla.
 */
public class VentaNoFacturableException extends GraphQLException {

    public VentaNoFacturableException(String mensaje) {
        super(mensaje);
    }

    /** La encuentra aunque venga envuelta (saveFacturaLegal re-envuelve lo que lanza el builder). */
    public static VentaNoFacturableException en(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof VentaNoFacturableException) {
                return (VentaNoFacturableException) t;
            }
        }
        return null;
    }
}
