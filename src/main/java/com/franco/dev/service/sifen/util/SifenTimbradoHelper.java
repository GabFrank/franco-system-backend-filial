package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.financiero.TimbradoDetalle;

/**
 * Datos del emisor que salen del timbrado. Espejo del helper del central, del que el filial solo
 * necesita {@link #telefonoEmisor}.
 */
public final class SifenTimbradoHelper {

    private SifenTimbradoHelper() {
    }

    /**
     * Telefono del emisor (dTelEmi): el del detalle del timbrado y, si esta vacio, el de la cabecera.
     * Nunca devuelve cadena vacia: jsifenlib escribe el elemento siempre y SIFEN rechaza el lote con
     * «0160 XML malformado: [El valor del elemento: dTelEmi es invalido]» (paso el 2026-09-23 en el
     * central con el deposito 13, cuyo detalle no tenia telefono y la cabecera si). Si faltan los dos
     * devuelve null y {@code FacturaLegalBuilder} corta antes de escribir.
     */
    public static String telefonoEmisor(TimbradoDetalle detalle) {
        if (detalle == null) {
            return null;
        }
        String telefono = recortado(detalle.getTelefono());
        if (telefono == null && detalle.getTimbrado() != null) {
            telefono = recortado(detalle.getTimbrado().getTelefono());
        }
        return telefono;
    }

    /**
     * Mensaje del corte por falta de telefono. El prefijo es fijo para poder buscarlo en el log: una
     * factura silenciosa que no se emite por esto no deja ninguna fila, solo esta linea.
     */
    public static String mensajeSinTelefono(TimbradoDetalle detalle) {
        return "TIMBRADO_SIN_TELEFONO: el timbrado " + detalle.getTimbrado().getNumero()
                + " (detalle " + detalle.getId() + ") no tiene telefono cargado ni en el detalle ni en"
                + " la cabecera: SIFEN rechazaria la factura";
    }

    private static String recortado(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}
