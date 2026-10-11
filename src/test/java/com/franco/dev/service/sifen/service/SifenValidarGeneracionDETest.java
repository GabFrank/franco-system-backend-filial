package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.productos.Producto;
import com.roshka.sifen.core.SifenConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El ensayo del DE (issue #170) corre sobre la factura y los items todavia sin guardar, con el
 * armado real. Dos cosas tienen que ser ciertas: que corte lo que el armado rechaza (hoy eso pierde
 * la venta ya cobrada) y, sobre todo, que NO corte una factura valida: el ensayo corre en todas las
 * facturas, y un falso positivo dejaria de facturar en silencio.
 */
class SifenValidarGeneracionDETest {

    /** Habilitado, sin certificado ni red: el armado no usa nada de eso. */
    private final SifenService service =
            new SifenService(null, null, null, null, null, null, null, null, new SifenConfig());

    private static FacturaLegal factura() {
        return FacturaElectronicaDePrueba.factura();
    }

    private static FacturaLegalItem item(String descripcion, float cantidad, double precio) {
        return FacturaElectronicaDePrueba.item(descripcion, cantidad, precio);
    }

    private static List<FacturaLegalItem> unItem() {
        return Collections.singletonList(item("ITEM", 1f, 11000.0));
    }

    private void pasa(FacturaLegal factura, List<FacturaLegalItem> items) {
        assertDoesNotThrow(() -> service.validarGeneracionDE(factura, items));
    }

    private Exception corta(FacturaLegal factura, List<FacturaLegalItem> items) {
        return assertThrows(Exception.class, () -> service.validarGeneracionDE(factura, items));
    }

    // ---- facturas validas: no se cortan ----

    @Test
    void validaSinIdNiCreadoEn_comoLlegaDelBuilder() {
        FacturaLegal f = factura();
        assertTrue(f.getId() == null && f.getCreadoEn() == null);
        assertTrue(service.isSifenEnabled());

        pasa(f, unItem());
    }

    @Test
    void validaACredito() {
        FacturaLegal f = factura();
        f.setCredito(true);

        pasa(f, unItem());
    }

    @Test
    void validaEnDolaresConTipoDeCambio() {
        FacturaLegal f = factura();
        f.setMonedaExtranjera("USD");
        f.setTipoCambio(7300.0);

        pasa(f, unItem());
    }

    @Test
    void validaConClienteConRuc() {
        FacturaLegal f = factura();
        Persona persona = new Persona();
        persona.setNombre("EMPRESA X SA");
        persona.setDocumento("80099482");
        Cliente cliente = new Cliente();
        cliente.setPersona(persona);
        cliente.setTributa(true);
        f.setCliente(cliente);

        pasa(f, unItem());
    }

    @Test
    void validaConItemPesable() {
        FacturaLegalItem pesable = item("QUESO", 0.23f, 50000.0);
        Producto producto = new Producto();
        producto.setBalanza(true);
        pesable.setProducto(producto);
        FacturaLegal f = factura();
        f.setTotalFinal(11500.0);

        pasa(f, Collections.singletonList(pesable));
    }

    @Test
    void validaConDeliveryComoItem() {
        FacturaLegal f = factura();
        f.setTotalFinal(16000.0);

        pasa(f, Arrays.asList(item("ITEM", 1f, 11000.0), item("SERVICIO DE DELIVERY", 1f, 5000.0)));
    }

    @Test
    void validaConDescuentoGlobal() {
        FacturaLegal f = factura();
        f.setDescuento(1000.0);
        f.setTotalFinal(20000.0);

        pasa(f, Arrays.asList(item("A", 3f, 5000.0), item("B", 1f, 6000.0)));
    }

    // ---- lo que el armado rechaza: se corta ----

    @Test
    void itemAPrecioCero_corta() {
        Exception e = corta(factura(), Arrays.asList(item("ITEM", 1f, 11000.0), item("REGALO", 1f, 0.0)));

        assertTrue(e.getMessage().contains("REGALO"), e.getMessage());
    }

    @Test
    void itemConCantidadCero_corta() {
        corta(factura(), Collections.singletonList(item("ITEM", 0f, 11000.0)));
    }

    @Test
    void sucursalSinCodigoDeEstablecimiento_corta() {
        FacturaLegal f = factura();
        f.getTimbradoDetalle().getSucursal().setCodigoEstablecimientoFactura(" ");

        corta(f, unItem());
    }

    @Test
    void innominadaDeSieteMillonesOMas_corta() {
        FacturaLegal f = factura();
        f.setTotalFinal(7000000.0);

        corta(f, Collections.singletonList(item("TV", 1f, 7000000.0)));
    }

    /** El builder calcula el total antes de ensayar: si llegara nulo, el armado lanza. */
    @Test
    void totalFinalNulo_corta() {
        FacturaLegal f = factura();
        f.setTotalFinal(null);

        corta(f, unItem());
    }

    /** El armado no lo ve: reventaba despues de escribir, o al generar el XML. */
    @Test
    void sinItems_corta() {
        corta(factura(), Collections.emptyList());
        corta(factura(), null);
    }

    /** El armado no lo ve: daba NullPointerException al generar el XML. */
    @Test
    void enDolaresSinTipoDeCambio_corta() {
        FacturaLegal f = factura();
        f.setMonedaExtranjera("USD");

        corta(f, unItem());
    }

    /** Un codigo que no es una moneda conocida se factura en guaranies: no hace falta cambio. */
    @Test
    void monedaQueNoEsExtranjeraSinTipoDeCambio_noCorta() {
        FacturaLegal f = factura();
        f.setMonedaExtranjera("GS");

        pasa(f, unItem());
    }

    /** Tiene tipo propio: es lo unico que el cajero corrige solo, y el builder lo trata aparte. */
    @Test
    void innominadaDeSieteMillones_lanzaElTipoPropio() {
        FacturaLegal f = factura();
        f.setTotalFinal(7000000.0);

        assertThrows(com.franco.dev.service.sifen.util.SifenReceptorHelper.FacturaInnominadaSobreTopeException.class,
                () -> service.validarGeneracionDE(f, Collections.singletonList(item("TV", 1f, 7000000.0))));
    }
}
