package com.franco.dev.service.sorteo;

import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SorteoCalculoTest {

    private static final Set<Long> TARJETAS = new HashSet<>(Collections.singletonList(5L));

    private static CobroDetalleInput descuento(double valor, Double cambio) {
        CobroDetalleInput d = new CobroDetalleInput();
        d.setDescuento(true);
        d.setValor(valor);
        d.setCambio(cambio);
        return d;
    }

    private static CobroDetalleInput aumento(double valor) {
        CobroDetalleInput d = new CobroDetalleInput();
        d.setAumento(true);
        d.setValor(valor);
        return d;
    }

    private static CobroDetalleInput pago(long formaPagoId, double valor, Double cambio) {
        CobroDetalleInput d = new CobroDetalleInput();
        d.setPago(true);
        d.setFormaPagoId(formaPagoId);
        d.setValor(valor);
        d.setCambio(cambio);
        return d;
    }

    @Test
    public void igualAlMinimoCalifica() {
        assertEquals(1, SorteoCalculo.tickets("UNA_POR_VENTA", 100000, 100000, null, null));
    }

    @Test
    public void porMultiplo() {
        assertEquals(2, SorteoCalculo.tickets("POR_MULTIPLO", 250000, 100000, null, null));
    }

    @Test
    public void porMultiploConTope() {
        assertEquals(3, SorteoCalculo.tickets("POR_MULTIPLO", 900000, 100000, null, 3));
    }

    @Test
    public void cantidadFija() {
        assertEquals(4, SorteoCalculo.tickets("CANTIDAD_FIJA", 150000, 100000, 4, null));
    }

    @Test
    public void debajoDelMinimo() {
        assertEquals(0, SorteoCalculo.tickets("UNA_POR_VENTA", 99999, 100000, null, null));
        assertEquals(0, SorteoCalculo.tickets("POR_MULTIPLO", 99999, 100000, null, null));
        assertEquals(0, SorteoCalculo.tickets("CANTIDAD_FIJA", 99999, 100000, 4, null));
    }

    @Test
    public void modoDesconocidoNoDaTickets() {
        assertEquals(0, SorteoCalculo.tickets("OTRO", 500000, 100000, null, null));
    }

    @Test
    public void descuentoSeResta() {
        assertEquals(90000, SorteoCalculo.montoComputable(120000, Collections.singletonList(descuento(30000, null)), TARJETAS, true));
        assertEquals(90000, SorteoCalculo.montoComputable(120000, Collections.singletonList(descuento(-30000, null)), TARJETAS, true));
    }

    @Test
    public void recargoNoSuma() {
        assertEquals(120000, SorteoCalculo.montoComputable(120000, Collections.singletonList(aumento(10000)), TARJETAS, true));
    }

    @Test
    public void tarjetaExcluida() {
        java.util.List<CobroDetalleInput> pagos = Arrays.asList(pago(5L, 150000, null), pago(1L, 50000, null));
        assertEquals(50000, SorteoCalculo.montoComputable(200000, pagos, TARJETAS, false));
        assertEquals(200000, SorteoCalculo.montoComputable(200000, pagos, TARJETAS, true));
    }

    @Test
    public void cambioNullEsUno() {
        assertEquals(190000, SorteoCalculo.montoComputable(200000, Collections.singletonList(descuento(10000, null)), TARJETAS, true));
        assertEquals(190000, SorteoCalculo.montoComputable(200000, Collections.singletonList(descuento(10000, 1.0)), TARJETAS, true));
        // con cambio distinto de 1 se convierte a guaranies
        assertEquals(150000, SorteoCalculo.montoComputable(200000, Collections.singletonList(descuento(10, 5000.0)), TARJETAS, true));
        assertEquals(50000, SorteoCalculo.montoComputable(200000, Collections.singletonList(pago(5L, 30, 5000.0)), TARJETAS, false));
    }

    @Test
    public void nuncaNegativoYTolerantAListasNulas() {
        assertEquals(0, SorteoCalculo.montoComputable(1000, Collections.singletonList(descuento(5000, null)), TARJETAS, true));
        assertEquals(1000, SorteoCalculo.montoComputable(1000, null, null, false));
    }
}
