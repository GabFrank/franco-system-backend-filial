package com.franco.dev.service.sifen.util;

import com.roshka.sifen.core.fields.request.de.TgCamIVA;
import com.roshka.sifen.core.types.TiAfecIVA;

import java.math.BigDecimal;

/**
 * Construye el grupo E730 (gCamIVA) de un item a partir del porcentaje de IVA resuelto.
 *
 * Reglas del MT v150 para los campos E731-E737:
 *   - Gravado (E731=1): E733 dPropIVA = 100 (regla 1904) y E734 dTasaIVA = 5 o 10 (regla 1908).
 *   - Exento  (E731=3): E733 dPropIVA = 0   (regla 1905) y E734 dTasaIVA = 0      (regla 1907).
 *
 * Los siete campos del grupo son obligatorios en el XSD, y jsifenlib los serializa con
 * String.valueOf(): un BigDecimal en null se emite como el texto "null", el DE no valida
 * contra el esquema y SIFEN lo descarta sin devolver resultado individual en el lote.
 *
 * dBasGravIVA, dLiqIVAItem y dBasExe no se setean aca a proposito: jsifenlib los calcula y
 * los fuerza a 0 para exentos, que es lo que piden el MT (E735) y la NT13 (E737).
 */
public final class GCamIvaMapper {

    private GCamIvaMapper() {
    }

    /**
     * @param iva porcentaje de IVA del item (0, 5 o 10). Null se trata como 10, igual que el
     *            default de {@code IvaResolver}.
     */
    public static TgCamIVA construir(Integer iva) {
        TgCamIVA gCamIVA = new TgCamIVA();
        int porcentaje = (iva != null) ? iva : 10;

        if (porcentaje == 0) {
            gCamIVA.setiAfecIVA(TiAfecIVA.EXENTO);
            gCamIVA.setdPropIVA(BigDecimal.ZERO);
            gCamIVA.setdTasaIVA(BigDecimal.ZERO);
        } else {
            gCamIVA.setiAfecIVA(TiAfecIVA.GRAVADO);
            gCamIVA.setdPropIVA(BigDecimal.valueOf(100));
            gCamIVA.setdTasaIVA(BigDecimal.valueOf(porcentaje == 5 ? 5 : 10));
        }

        return gCamIVA;
    }
}
