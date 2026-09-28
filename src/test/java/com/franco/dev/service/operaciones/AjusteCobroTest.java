package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El descuento y el aumento de una venta viven como lineas del cobro. Esta cuenta la usan el ticket
 * simple, la factura silenciosa y la factura con DE: si cambia, cambian los tres comprobantes.
 */
public class AjusteCobroTest {

    private static CobroDetalle detalle(Double valor, Double cambio, boolean descuento, boolean aumento,
                                        boolean pago, boolean vuelto) {
        CobroDetalle cd = new CobroDetalle();
        cd.setValor(valor);
        cd.setCambio(cambio);
        cd.setDescuento(descuento);
        cd.setAumento(aumento);
        cd.setPago(pago);
        cd.setVuelto(vuelto);
        return cd;
    }

    private static CobroDetalle descuento(Double valor, Double cambio) {
        return detalle(valor, cambio, true, false, false, false);
    }

    private static CobroDetalle aumento(Double valor, Double cambio) {
        return detalle(valor, cambio, false, true, false, false);
    }

    @Test
    void soloDescuento() {
        AjusteCobro a = AjusteCobro.deDetalles(Collections.singletonList(descuento(500.0, 1.0)));
        assertEquals(500.0, a.getDescuento(), 0.001);
        assertEquals(0.0, a.getAumento(), 0.001);
        assertEquals(500.0, a.getNeto(), 0.001);
    }

    @Test
    void soloAumento() {
        AjusteCobro a = AjusteCobro.deDetalles(Collections.singletonList(aumento(300.0, 1.0)));
        assertEquals(0.0, a.getDescuento(), 0.001);
        assertEquals(300.0, a.getAumento(), 0.001);
        assertEquals(-300.0, a.getNeto(), 0.001);
    }

    @Test
    void descuentoYAumentoDanElNeto() {
        AjusteCobro a = AjusteCobro.deDetalles(Arrays.asList(descuento(1000.0, 1.0), aumento(200.0, 1.0)));
        assertEquals(800.0, a.getNeto(), 0.001);
    }

    @Test
    void variasLineasDeDescuentoSeSumanTodas() {
        // Hay cobros con mas de una linea de descuento (venta 73489: 50.400 + 5.500 + 100).
        AjusteCobro a = AjusteCobro.deDetalles(Arrays.asList(
                descuento(50400.0, 1.0), descuento(5500.0, 1.0), descuento(100.0, 1.0)));
        assertEquals(56000.0, a.getDescuento(), 0.001);
    }

    @Test
    void elCambioConvierteAGuaranies() {
        AjusteCobro a = AjusteCobro.deDetalles(Collections.singletonList(descuento(2.0, 7300.0)));
        assertEquals(14600.0, a.getDescuento(), 0.001);
    }

    @Test
    void cambioNuloCuentaComoGuaranies() {
        // 343 descuentos de 2023-02 a 2024-01 quedaron con cambio NULL, todos en guaranies. Antes
        // valor * cambio tiraba NullPointerException al reimprimir o facturar esas ventas.
        AjusteCobro a = AjusteCobro.deDetalles(Collections.singletonList(descuento(2000.0, null)));
        assertEquals(2000.0, a.getDescuento(), 0.001);
    }

    @Test
    void valorNuloNoSuma() {
        AjusteCobro a = AjusteCobro.deDetalles(Collections.singletonList(descuento(null, 1.0)));
        assertEquals(0.0, a.getDescuento(), 0.001);
    }

    @Test
    void pagosYVueltosNoSonAjuste() {
        AjusteCobro a = AjusteCobro.deDetalles(Arrays.asList(
                detalle(6000.0, 1.0, false, false, true, false),
                detalle(-500.0, 1.0, false, false, false, true),
                descuento(500.0, 1.0)));
        assertEquals(500.0, a.getDescuento(), 0.001);
        assertEquals(0.0, a.getAumento(), 0.001);
    }

    @Test
    void banderasNulasNoSonAjuste() {
        CobroDetalle cd = new CobroDetalle();
        cd.setValor(1000.0);
        cd.setCambio(1.0);
        AjusteCobro a = AjusteCobro.deDetalles(Collections.singletonList(cd));
        assertEquals(0.0, a.getNeto(), 0.001);
    }

    @Test
    void listaVaciaONulaEsSinAjuste() {
        assertEquals(0.0, AjusteCobro.deDetalles(new ArrayList<>()).getNeto(), 0.001);
        assertEquals(0.0, AjusteCobro.deDetalles(null).getNeto(), 0.001);
        assertEquals(0.0, AjusteCobro.deInputs(null).getNeto(), 0.001);
    }

    @Test
    void deInputsHaceLaMismaCuenta() {
        CobroDetalleInput desc = new CobroDetalleInput();
        desc.setValor(500.0);
        desc.setCambio(null);
        desc.setDescuento(true);
        CobroDetalleInput aum = new CobroDetalleInput();
        aum.setValor(100.0);
        aum.setCambio(1.0);
        aum.setAumento(true);
        CobroDetalleInput pago = new CobroDetalleInput();
        pago.setValor(6000.0);
        pago.setCambio(1.0);
        pago.setPago(true);
        List<CobroDetalleInput> inputs = Arrays.asList(desc, aum, pago);

        AjusteCobro a = AjusteCobro.deInputs(inputs);
        assertEquals(500.0, a.getDescuento(), 0.001);
        assertEquals(100.0, a.getAumento(), 0.001);
        assertEquals(400.0, a.getNeto(), 0.001);
    }

    @Test
    void sinAjusteEsCero() {
        assertEquals(0.0, AjusteCobro.SIN_AJUSTE.getNeto(), 0.001);
    }
}
