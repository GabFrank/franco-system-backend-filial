package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;

/**
 * Politica de facturacion ya resuelta para esta sucursal, sin NULLs: lo que devuelve
 * {@link ConfiguracionFacturacionLector#resolver()} y lo que consume {@link PoliticaFacturacionService}.
 * Inmutable, para que una venta decida con una sola foto de la configuracion.
 */
public final class PoliticaFacturacion {

    /** De donde salio la politica. Solo para el log: no cambia ninguna decision. */
    public enum Origen { SUCURSAL, GLOBAL, PROPERTY }

    private final String modo;
    private final int ventasSinFactura;
    private final boolean ventaTicketRespetaPolitica;
    private final Origen origen;

    public PoliticaFacturacion(String modo, int ventasSinFactura, boolean ventaTicketRespetaPolitica, Origen origen) {
        this.modo = modo;
        this.ventasSinFactura = ventasSinFactura;
        this.ventaTicketRespetaPolitica = ventaTicketRespetaPolitica;
        this.origen = origen;
    }

    /**
     * El comportamiento anterior a la tabla: el contador facturaCountDown de la property, y
     * "Venta + Ticket" facturando siempre.
     */
    public static PoliticaFacturacion desdeProperty(int facturaCountDown) {
        return new PoliticaFacturacion(ConfiguracionFacturacion.MODO_INTERVALO, facturaCountDown, false, Origen.PROPERTY);
    }

    public String getModo() {
        return modo;
    }

    public int getVentasSinFactura() {
        return ventasSinFactura;
    }

    public boolean isVentaTicketRespetaPolitica() {
        return ventaTicketRespetaPolitica;
    }

    public Origen getOrigen() {
        return origen;
    }

    @Override
    public String toString() {
        return "PoliticaFacturacion{modo=" + modo + ", ventasSinFactura=" + ventasSinFactura
                + ", ventaTicketRespetaPolitica=" + ventaTicketRespetaPolitica + ", origen=" + origen + "}";
    }
}
