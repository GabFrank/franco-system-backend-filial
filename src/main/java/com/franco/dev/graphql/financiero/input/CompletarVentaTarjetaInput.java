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

    /**
     * De donde salieron estos datos: QR | OCR | MANUAL | API.
     * <p>
     * Lo manda el cliente porque es el unico que sabe por que camino los obtuvo: el backend ve
     * exactamente la misma mutation en los cuatro casos. Si no viene, el servidor deduce QR
     * cuando hay qrCrudo y deja NULL en el resto --OCR y MANUAL son indistinguibles desde el
     * backend, y un 'OCR' inventado sobre una carga a mano haria que la conciliacion confie en
     * un dato que un humano tipeo.
     * <p>
     * Opcional, como todo campo nuevo de input en este repo: `mobile` sigue instalada, consume
     * esta mutation y solo se actualiza por release manual de Play Store.
     */
    private String origen;

    /**
     * Token de la captura de la que salieron estos datos, cuando vinieron de una foto.
     * <p>
     * <b>Es lo que ata la foto a la venta.</b> Sin esto la imagen queda colgando en
     * {@code captura_cupon} sin ninguna relacion con el cobro: no hay FK, no hay columna, y el
     * job de purga no tiene forma de distinguir una foto huerfana de la evidencia de una venta
     * que manana se discute. Al completar se copia la ruta a {@code venta_tarjeta.imagen_url},
     * que ya existia y estaba muerta.
     * <p>
     * Opcional, como todo campo nuevo de input en este repo.
     */
    private String capturaToken;
}
