package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.FacturaLegalItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EA004 ({@code dDescGloItem}) es el descuento global sobre el PRECIO UNITARIO. El Manual
 * Tecnico v150 define EA008 ({@code dTotOpeItem}) como
 *
 * <pre>(precio_unitario - desc_particular - desc_global - anticipos) * cantidad</pre>
 *
 * El codigo mandaba el descuento de la linea entera, asi que SIFEN lo multiplicaba de nuevo
 * por la cantidad. Con cantidad=1 los dos valores coinciden y por eso paso inadvertido.
 *
 * Medido en bodega el 2026-09-21, ultimos 60 dias: 163 rechazos 1862 (los 163 con descuento)
 * y 43 rechazos 0160 por {@code dTotOpeItem} negativo. Caso real, factura 517743:
 * HIELO MEDIANO, cantidad 10, precio 5000, descuento 25000 -> se mando dDescGloItem=25000
 * y SIFEN calculo (5000-25000)*10 = -200000.
 */
class SifenDescuentoGlobalTest {

    private static FacturaLegalItem item(double total) {
        FacturaLegalItem i = new FacturaLegalItem();
        i.setTotal(total);
        return i;
    }

    /** dTotOpeItem tal como lo calcula SIFEN, para afirmar sobre el resultado real. */
    private static BigDecimal totOpeItem(BigDecimal precioUnitario, BigDecimal descGloItem,
                                         BigDecimal cantidad) {
        return precioUnitario.subtract(descGloItem).multiply(cantidad);
    }

    @Test
    void sinDescuentoGlobalTodoEnCero() {
        BigDecimal[] r = SifenService.prorratearDescuentoGlobal(
                Arrays.asList(item(50000.0), item(30000.0)), 0.0, 80000.0);
        assertEquals(BigDecimal.ZERO, r[0]);
        assertEquals(BigDecimal.ZERO, r[1]);
    }

    @Test
    void totalBrutoCeroNoDivide() {
        BigDecimal[] r = SifenService.prorratearDescuentoGlobal(
                Collections.singletonList(item(0.0)), 5000.0, 0.0);
        assertEquals(BigDecimal.ZERO, r[0]);
    }

    @Test
    void listaVaciaNoRevienta() {
        assertEquals(0, SifenService.prorratearDescuentoGlobal(new ArrayList<>(), 5000.0, 1000.0).length);
    }

    /**
     * El caso de produccion. Con el descuento por unidad, dTotOpeItem vuelve a ser el
     * total_final de la factura en vez de -200000.
     */
    @Test
    void casoFactura517743_cantidadDiezDejaDeDarNegativo() {
        BigDecimal[] porLinea = SifenService.prorratearDescuentoGlobal(
                Collections.singletonList(item(50000.0)), 25000.0, 50000.0);
        assertEquals(0, porLinea[0].compareTo(BigDecimal.valueOf(25000)));

        BigDecimal cantidad = BigDecimal.valueOf(10);
        BigDecimal descUnitario = porLinea[0].divide(cantidad, 4, RoundingMode.HALF_UP);
        assertEquals(0, descUnitario.compareTo(BigDecimal.valueOf(2500)));

        BigDecimal total = totOpeItem(BigDecimal.valueOf(5000), descUnitario, cantidad);
        assertEquals(0, total.compareTo(BigDecimal.valueOf(25000)));
        assertTrue(total.signum() > 0, "dTotOpeItem no puede ser negativo");
    }

    /** Con cantidad 1 el comportamiento no cambia: es el caso que siempre funciono. */
    @Test
    void cantidadUnoSigueIgual() {
        BigDecimal[] porLinea = SifenService.prorratearDescuentoGlobal(
                Collections.singletonList(item(5500.0)), 500.0, 5500.0);
        BigDecimal descUnitario = porLinea[0].divide(BigDecimal.ONE, 4, RoundingMode.HALF_UP);
        assertEquals(0, totOpeItem(BigDecimal.valueOf(5500), descUnitario, BigDecimal.ONE)
                .compareTo(BigDecimal.valueOf(5000)));
    }

    /**
     * F033 (dTotDescGlotem) es la suma llana de EA004. Redondear cada linea por separado
     * hacia que no cerrara contra el descuento informado: 1862.
     */
    @Test
    void elResiduoDeRedondeoCierraEnElUltimoItem() {
        // 1000 repartido entre tres lineas iguales: 333,33 cada una
        List<FacturaLegalItem> items = Arrays.asList(item(1000.0), item(1000.0), item(1000.0));
        BigDecimal[] r = SifenService.prorratearDescuentoGlobal(items, 1000.0, 3000.0);

        BigDecimal suma = Arrays.stream(r).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, suma.compareTo(BigDecimal.valueOf(1000)),
                "la suma de los descuentos por linea debe dar el descuento global exacto");
        assertEquals(0, r[2].compareTo(BigDecimal.valueOf(334)), "el ultimo absorbe el residuo");
    }

    @Test
    void repartoProporcionalAlPesoDeCadaItem() {
        List<FacturaLegalItem> items = Arrays.asList(item(75000.0), item(25000.0));
        BigDecimal[] r = SifenService.prorratearDescuentoGlobal(items, 10000.0, 100000.0);
        assertEquals(0, r[0].compareTo(BigDecimal.valueOf(7500)));
        assertEquals(0, r[1].compareTo(BigDecimal.valueOf(2500)));
        assertEquals(0, Arrays.stream(r).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(BigDecimal.valueOf(10000)));
    }

    /**
     * Moneda extranjera: el precio unitario se convierte a la divisa antes del bloque de
     * descuento, asi que el descuento tiene que convertirse ANTES de compararse contra el.
     * Comparar sin convertir enfrentaba miles de guaranies contra unidades de dolar y la
     * guarda saltaba siempre, dejando sin DE a toda factura en divisa con descuento global.
     */
    @Test
    void enMonedaExtranjeraElDescuentoSeConvierteAntesDeCompararlo() {
        BigDecimal tipoCambio = BigDecimal.valueOf(7300);
        // precio 73.000 Gs -> 10 USD; descuento 5.000 Gs -> 0,6849 USD
        BigDecimal precioUnitarioUsd = BigDecimal.valueOf(73000)
                .divide(tipoCambio, 4, RoundingMode.HALF_UP);

        BigDecimal[] porLinea = SifenService.prorratearDescuentoGlobal(
                Collections.singletonList(item(73000.0)), 5000.0, 73000.0);
        BigDecimal descuentoUnitarioGs = porLinea[0].divide(BigDecimal.ONE, 4, RoundingMode.HALF_UP);
        BigDecimal descuentoUnitarioUsd = descuentoUnitarioGs.divide(tipoCambio, 4, RoundingMode.HALF_UP);

        assertTrue(descuentoUnitarioGs.compareTo(precioUnitarioUsd) > 0,
                "sin convertir, el descuento en Gs supera al precio en USD: es el falso positivo");
        assertTrue(descuentoUnitarioUsd.compareTo(precioUnitarioUsd) <= 0,
                "convertido, el descuento no supera el precio y la guarda no debe saltar");

        BigDecimal total = totOpeItem(precioUnitarioUsd, descuentoUnitarioUsd, BigDecimal.ONE);
        assertTrue(total.signum() > 0, "dTotOpeItem en divisa no puede ser negativo");
    }

    /**
     * Con muchas lineas, el exceso de redondeo acumulado de las primeras puede pasarse del
     * descuento global y dejar al ultimo en negativo. Antes se clampeaba a cero, lo que
     * arreglaba el signo pero rompia la suma exacta: quedaba por encima del global.
     */
    @Test
    void conMuchasLineasNiQuedaNegativoNiSePasaLaSuma() {
        List<FacturaLegalItem> items = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            items.add(item(1000.0));
        }
        items.add(item(1.0));   // ultimo con peso casi nulo: absorbe poco y puede quedar corto
        BigDecimal[] r = SifenService.prorratearDescuentoGlobal(items, 21.0, 40001.0);

        for (int i = 0; i < r.length; i++) {
            assertTrue(r[i].signum() >= 0, "ninguna linea puede quedar negativa (indice " + i + ")");
        }
        assertEquals(0, Arrays.stream(r).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .compareTo(BigDecimal.valueOf(21)),
                "la suma tiene que cerrar exacta contra el descuento global");
    }
}
