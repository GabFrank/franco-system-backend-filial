package com.franco.dev.graphql.financiero.publisher;

import lombok.Data;

/**
 * Aviso de que una captura de cupon termino: el telefono subio la foto y el OCR ya corrio.
 *
 * <p>Viaja el desenlace, no la entidad: el desktop solo necesita saber si hay texto o si hubo
 * un problema, y el resto ya lo tiene. Se publica tambien en {@code ERROR}, porque el cajero
 * suele estar mirando la pantalla de la caja y no el telefono.
 *
 * <p>Lleva {@code cajaId} porque en una sucursal hay varias cajas escuchando el mismo canal:
 * cada desktop descarta lo que no es suyo. El filtro real, igual, es el {@code token}.
 */
@Data
public class CapturaCuponUpdate {

    private String token;

    private Long cajaId;

    /** ESPERANDO / PROCESANDO / LISTO / ERROR. */
    private String estado;

    /** El texto crudo del OCR, una linea por caja detectada. Null si no hubo lectura. */
    private String textoOcr;

    /** Motivo entendible por un cajero cuando el estado es ERROR. */
    private String error;

    private Integer msOcr;
}
