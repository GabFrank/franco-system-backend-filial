package com.franco.dev.utilitarios.print;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Los totales en moneda extranjera de un ticket llegan nulos cuando la venta se registro sin
 * cotizacion cargada. El ticket tiene que imprimirse igual.
 */
class TicketFormatoTest {

    @Test
    @DisplayName("un total nulo se imprime como '-' en vez de reventar")
    void totalNuloImprimeGuion() {
        // Con el codigo viejo, venta.getTotalRs() + precioDeliveryRs con total nulo es un
        // NullPointerException al desboxear, y el ticket de credito no sale.
        assertEquals(TicketFormato.SIN_COTIZACION, TicketFormato.formatearTotalMoneda(null, 5.0));
        assertEquals(TicketFormato.SIN_COTIZACION, TicketFormato.formatearTotalMoneda(null, null));
    }

    @Test
    @DisplayName("con total, suma el extra y formatea con dos decimales")
    void totalConExtra() {
        assertEquals(String.format("%.2f", 12.5), TicketFormato.formatearTotalMoneda(10.0, 2.5));
    }

    @Test
    @DisplayName("un extra nulo cuenta como cero")
    void extraNuloEsCero() {
        assertEquals(String.format("%.2f", 10.0), TicketFormato.formatearTotalMoneda(10.0, null));
    }

    @Test
    @DisplayName("mismo formato que el codigo anterior para un total presente")
    void mismoFormatoQueAntes() {
        // El cambio no puede alterar lo que se imprime cuando hay cotizacion.
        Double total = 1234.567;
        Double delivery = 3.0;
        assertEquals(String.format("%.2f", total + delivery),
                TicketFormato.formatearTotalMoneda(total, delivery));
    }
}
