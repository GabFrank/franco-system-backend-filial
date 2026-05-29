package com.franco.dev.service.financiero.builder;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParcialesCalculatorTest {

    private static final double DELTA = 0.01;

    @Test
    void calculaSinDescuento_unicaBanda10() {
        List<ParcialesCalculator.ItemIvaTuple> items = Collections.singletonList(
                new ParcialesCalculator.ItemIvaTuple(10, 11000.0));
        ParcialesCalculator.Resultado r = ParcialesCalculator.calcular(items, 0.0);
        assertEquals(0.0, r.getTotalParcial0(), DELTA);
        assertEquals(0.0, r.getTotalParcial5(), DELTA);
        assertEquals(11000.0, r.getTotalParcial10(), DELTA);
        assertEquals(1000.0, r.getIvaParcial10(), DELTA);
        assertEquals(0.0, r.getIvaParcial5(), DELTA);
        assertEquals(11000.0, r.getTotalFinal(), DELTA);
    }

    @Test
    void distribuyeDescuentoProporcional_mixBandas() {
        // p0=10000, p10=11000, p5=2100, total=23100, descuento=2310 (10%)
        // post: p0=9000, p10=9900, p5=1890, total=20790
        List<ParcialesCalculator.ItemIvaTuple> items = Arrays.asList(
                new ParcialesCalculator.ItemIvaTuple(0, 10000.0),
                new ParcialesCalculator.ItemIvaTuple(10, 11000.0),
                new ParcialesCalculator.ItemIvaTuple(5, 2100.0));
        ParcialesCalculator.Resultado r = ParcialesCalculator.calcular(items, 2310.0);
        assertEquals(9000.0, r.getTotalParcial0(), DELTA);
        assertEquals(1890.0, r.getTotalParcial5(), DELTA);
        assertEquals(9900.0, r.getTotalParcial10(), DELTA);
        assertEquals(20790.0, r.getTotalFinal(), DELTA);
        // iva proporcional al parcial post-descuento
        assertEquals(9900.0 / 11.0, r.getIvaParcial10(), DELTA);
        assertEquals(1890.0 / 21.0, r.getIvaParcial5(), DELTA);
    }

    @Test
    void ivaParcialDesdeNeto_noBruto() {
        // verify formula iva10 = parcial_neto_10 / 11, no bruto / 11
        List<ParcialesCalculator.ItemIvaTuple> items = Collections.singletonList(
                new ParcialesCalculator.ItemIvaTuple(10, 11000.0));
        ParcialesCalculator.Resultado r = ParcialesCalculator.calcular(items, 1100.0);
        // bruto 11000 - descuento 1100 = 9900 → iva10 = 9900/11 = 900
        assertEquals(9900.0, r.getTotalParcial10(), DELTA);
        assertEquals(900.0, r.getIvaParcial10(), DELTA);
    }

    @Test
    void totalFinalIgualSumaParciales() {
        List<ParcialesCalculator.ItemIvaTuple> items = Arrays.asList(
                new ParcialesCalculator.ItemIvaTuple(0, 50000.0),
                new ParcialesCalculator.ItemIvaTuple(5, 21000.0),
                new ParcialesCalculator.ItemIvaTuple(10, 11000.0));
        ParcialesCalculator.Resultado r = ParcialesCalculator.calcular(items, 8200.0);
        double suma = r.getTotalParcial0() + r.getTotalParcial5() + r.getTotalParcial10();
        assertEquals(r.getTotalFinal(), suma, DELTA);
    }

    @Test
    void todosItems0_parcialesCorrectos() {
        List<ParcialesCalculator.ItemIvaTuple> items = Arrays.asList(
                new ParcialesCalculator.ItemIvaTuple(0, 10000.0),
                new ParcialesCalculator.ItemIvaTuple(0, 5000.0));
        ParcialesCalculator.Resultado r = ParcialesCalculator.calcular(items, 0.0);
        assertEquals(15000.0, r.getTotalParcial0(), DELTA);
        assertEquals(0.0, r.getTotalParcial5(), DELTA);
        assertEquals(0.0, r.getTotalParcial10(), DELTA);
        assertEquals(15000.0, r.getTotalFinal(), DELTA);
    }

    @Test
    void sinItems_parcialesCero() {
        ParcialesCalculator.Resultado r = ParcialesCalculator.calcular(Collections.emptyList(), 0.0);
        assertEquals(0.0, r.getTotalFinal(), DELTA);
    }

    @Test
    void descuentoNegativo_aumentaProporcional() {
        // ajusteNeto < 0 = aumento (recargo). Ej. recargo 10% sobre 10000 → +1000
        List<ParcialesCalculator.ItemIvaTuple> items = Collections.singletonList(
                new ParcialesCalculator.ItemIvaTuple(10, 11000.0));
        ParcialesCalculator.Resultado r = ParcialesCalculator.calcular(items, -1100.0);
        // 11000 * 1.1 = 12100
        assertEquals(12100.0, r.getTotalParcial10(), DELTA);
        assertEquals(12100.0 / 11.0, r.getIvaParcial10(), DELTA);
        assertEquals(12100.0, r.getTotalFinal(), DELTA);
    }

    @Test
    void descuentoMayorQueBruto_noNegativos() {
        List<ParcialesCalculator.ItemIvaTuple> items = Collections.singletonList(
                new ParcialesCalculator.ItemIvaTuple(10, 11000.0));
        ParcialesCalculator.Resultado r = ParcialesCalculator.calcular(items, 99999.0);
        assertTrue(r.getTotalParcial10() >= 0.0);
        assertTrue(r.getTotalFinal() >= 0.0);
    }
}
