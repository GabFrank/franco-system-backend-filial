package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Datos extraidos del QR que imprime el POS. Es un input propio y no VentaTarjetaInput
 * a proposito: aca solo viajan los campos que el cupon aporta, para que sea imposible
 * pisar por accidente los que el PDV ya cargo (ventaId, cajaId, monto, terminal, usuario).
 */
@Data
public class CompletarVentaTarjetaInput {

    /** Id del registro PENDIENTE creado por el PDV al cerrar la venta. */
    private Long id;

    private Long sucursalId;

    private String codigoAutorizacion;

    private String numeroBoleta;

    /** Monto leido del cupon. Puede diferir del cobrado: se avisa, no se bloquea. */
    private BigDecimal montoEscaneado;

    /** Referencia unica del proveedor; se copia al CobroDetalle de TARJETA de la venta. */
    private String identificadorTransaccion;

    /** Cadena cruda tal como entro por el lector, para poder diagnosticar despues. */
    private String qrCrudo;

    /**
     * CobroDetalle al que pertenece este cupon, elegido explicitamente por el usuario.
     * Cuando viene, manda: no se infiere nada. Es el unico camino cuando la venta tiene dos
     * cobros con tarjeta del MISMO monto, porque ahi no hay dato para desempatarlos.
     */
    private Long cobroDetalleId;

    /**
     * Moneda que declara el cupon. Tiene que coincidir con la del cobro que se esta pagando: un
     * cupon en otra moneda no lo paga, y guardarlo igual deja un monto_escaneado sin unidad que
     * cuadra por casualidad en cualquier reporte.
     */
    private Long monedaId;
}
