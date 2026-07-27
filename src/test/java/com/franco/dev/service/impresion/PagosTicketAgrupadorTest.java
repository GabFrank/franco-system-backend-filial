package com.franco.dev.service.impresion;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.CobroDetalle;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagosTicketAgrupadorTest {

    private static final double DELTA = 0.01;

    private static final Long EFECTIVO_ID = 1L;
    private static final Long TARJETA_ID = 2L;

    private static CobroDetalle pago(Long formaPagoId, Double valor) {
        return detalle(formaPagoId, valor, true, false);
    }

    private static CobroDetalle vuelto(Long formaPagoId, Double valor) {
        return detalle(formaPagoId, valor, false, true);
    }

    private static CobroDetalle detalle(Long formaPagoId, Double valor, boolean esPago, boolean esVuelto) {
        Moneda moneda = new Moneda();
        moneda.setId(1L);

        CobroDetalle cd = new CobroDetalle();
        cd.setMoneda(moneda);
        cd.setValor(valor);
        cd.setPago(esPago);
        cd.setVuelto(esVuelto);
        if (formaPagoId != null) {
            FormaPago fp = new FormaPago();
            fp.setId(formaPagoId);
            cd.setFormaPago(fp);
        }
        return cd;
    }

    /**
     * Caso reportado: venta de 100.000 Gs pagada con dos billetes de 50.000 en efectivo.
     * El ticket debe mostrar 100.000, no 50.000.
     */
    @Test
    void sumaDosPagosConLaMismaFormaDePago() {
        List<PagosTicketAgrupador.PagoAgrupado> agrupados = PagosTicketAgrupador.agrupar(
                Arrays.asList(pago(EFECTIVO_ID, 50000.0), pago(EFECTIVO_ID, 50000.0)));

        assertEquals(1, agrupados.size(), "dos pagos en efectivo deben imprimirse en una sola linea");
        assertEquals(100000.0, agrupados.get(0).getValor(), DELTA);
    }

    @Test
    void mantieneSeparadasLasFormasDePagoDistintas() {
        List<PagosTicketAgrupador.PagoAgrupado> agrupados = PagosTicketAgrupador.agrupar(
                Arrays.asList(pago(EFECTIVO_ID, 30000.0), pago(TARJETA_ID, 70000.0)));

        assertEquals(2, agrupados.size());
        assertEquals(EFECTIVO_ID, agrupados.get(0).getFormaPago().getId());
        assertEquals(30000.0, agrupados.get(0).getValor(), DELTA);
        assertEquals(TARJETA_ID, agrupados.get(1).getFormaPago().getId());
        assertEquals(70000.0, agrupados.get(1).getValor(), DELTA);
    }

    @Test
    void respetaElOrdenDeAparicionAlSumarRepetidos() {
        List<PagosTicketAgrupador.PagoAgrupado> agrupados = PagosTicketAgrupador.agrupar(
                Arrays.asList(pago(TARJETA_ID, 10000.0), pago(EFECTIVO_ID, 20000.0), pago(TARJETA_ID, 5000.0)));

        assertEquals(2, agrupados.size());
        assertEquals(TARJETA_ID, agrupados.get(0).getFormaPago().getId(), "la primera forma vista va primero");
        assertEquals(15000.0, agrupados.get(0).getValor(), DELTA);
        assertEquals(20000.0, agrupados.get(1).getValor(), DELTA);
    }

    @Test
    void ignoraVueltosYDetallesQueNoSonPago() {
        List<PagosTicketAgrupador.PagoAgrupado> agrupados = PagosTicketAgrupador.agrupar(
                Arrays.asList(pago(EFECTIVO_ID, 100000.0), vuelto(EFECTIVO_ID, 20000.0)));

        assertEquals(1, agrupados.size());
        assertEquals(100000.0, agrupados.get(0).getValor(), DELTA);
    }

    @Test
    void agrupaPagosSinFormaDePagoEnUnaSolaLinea() {
        List<PagosTicketAgrupador.PagoAgrupado> agrupados = PagosTicketAgrupador.agrupar(
                Arrays.asList(pago(null, 40000.0), pago(null, 10000.0)));

        assertEquals(1, agrupados.size());
        assertEquals(50000.0, agrupados.get(0).getValor(), DELTA);
    }

    @Test
    void toleraValorNuloYListaVacia() {
        List<PagosTicketAgrupador.PagoAgrupado> conValorNulo = PagosTicketAgrupador.agrupar(
                Arrays.asList(pago(EFECTIVO_ID, 50000.0), pago(EFECTIVO_ID, null)));
        assertEquals(1, conValorNulo.size());
        assertEquals(50000.0, conValorNulo.get(0).getValor(), DELTA);

        assertTrue(PagosTicketAgrupador.agrupar(Collections.emptyList()).isEmpty());
        assertTrue(PagosTicketAgrupador.agrupar(null).isEmpty());
    }
}
