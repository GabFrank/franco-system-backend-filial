package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.service.financiero.PoliticaFacturacionService.RutaVenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fija la decision de {@code saveVenta} (issue #127).
 *
 * <p>El test que mas importa es la tabla de verdad: con la politica por defecto (la property
 * facturaCountDown), la ruta y el contador tienen que ser los mismos que con la logica vieja en
 * TODAS las combinaciones, salvo las filas que el plan declara como cambio de comportamiento.
 */
public class PoliticaFacturacionServiceTest {

    private static final Long PDV = 1L;

    private PoliticaFacturacionService service;

    @BeforeEach
    public void setUp() {
        service = new PoliticaFacturacionService();
    }

    private static PoliticaFacturacion politica(String modo, int n, boolean respeta) {
        return new PoliticaFacturacion(modo, n, respeta, PoliticaFacturacion.Origen.GLOBAL);
    }

    // ── La logica vieja, trasplantada de VentaGraphQL.saveVenta (filial fdc321e, lineas 261-336) ──

    /** Lo que quedo del contador viejo despues de decidir; es un campo para imitar al del resolver. */
    private Integer contadorViejo;

    /** Devuelve null cuando la logica vieja revienta (el NPE que el catch del resolver tragaba). */
    private RutaVenta logicaVieja(Boolean ticket, Boolean facturar, Long pdvId, boolean credito, int property) {
        try {
            if (ticket != null && ticket == true) {
                if (pdvId != null && facturar) {
                    return RutaVenta.FACTURA_E_IMPRESION;
                } else if (credito) {
                    return RutaVenta.PAGARE_CREDITO;
                } else {
                    return RutaVenta.TICKET_SIMPLE;
                }
            } else if (facturar != null && !facturar) {
                return RutaVenta.SIN_FACTURA;
            } else if (contadorViejo == 0) {
                if (pdvId != null) {
                    contadorViejo = property;
                    return RutaVenta.FACTURA_SILENCIOSA;
                }
                return RutaVenta.SIN_FACTURA;
            } else {
                contadorViejo = contadorViejo - 1;
                return RutaVenta.SIN_FACTURA;
            }
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Test
    public void conLaPoliticaPorDefectoDecideIgualQueLaLogicaVieja() {
        Boolean[] tresEstados = {null, false, true};
        Long[] pdvs = {null, PDV};
        boolean[] creditos = {false, true};
        int[] contadores = {0, 2};
        int property = 2;
        int comparadas = 0;

        for (Boolean ticket : tresEstados) {
            for (Boolean facturar : tresEstados) {
                for (Long pdvId : pdvs) {
                    for (boolean credito : creditos) {
                        for (int inicial : contadores) {
                            String caso = "ticket=" + ticket + " facturar=" + facturar + " pdv=" + pdvId
                                    + " credito=" + credito + " contador=" + inicial;
                            contadorViejo = inicial;
                            RutaVenta vieja = logicaVieja(ticket, facturar, pdvId, credito, property);

                            service.fijarContador(inicial);
                            RutaVenta nueva = service.decidirRuta(ticket, facturar, pdvId, credito,
                                    PoliticaFacturacion.desdeProperty(property));

                            if (vieja == null) {
                                // Unico cambio declarado: ticket=true, facturar=null, con pdv reventaba.
                                assertTrue(Boolean.TRUE.equals(ticket) && facturar == null && pdvId != null, caso);
                                assertEquals(credito ? RutaVenta.PAGARE_CREDITO : RutaVenta.TICKET_SIMPLE, nueva, caso);
                                assertEquals(Integer.valueOf(inicial), service.contadorActual(), caso);
                            } else {
                                assertEquals(vieja, nueva, caso);
                                assertEquals(contadorViejo, service.contadorActual(), caso);
                                comparadas++;
                            }
                        }
                    }
                }
            }
        }
        // 3 * 3 * 2 * 2 * 2 = 72 combinaciones; 4 son las del NPE (ticket=true, facturar=null, pdv, x2 credito, x2 contador).
        assertEquals(68, comparadas);
    }

    @Test
    public void ticketConFacturarNuloYaNoRevientaYSaleTicketSimple() {
        RutaVenta ruta = service.decidirRuta(true, null, PDV, false, PoliticaFacturacion.desdeProperty(0));
        assertEquals(RutaVenta.TICKET_SIMPLE, ruta);
    }

    // ── El bypass del issue #127 ──

    @Test
    public void ventaTicketQueRespetaLaPoliticaSinTurnoNoFactura() {
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_INTERVALO, 2, true);
        service.fijarContador(2);
        assertEquals(RutaVenta.TICKET_SIMPLE, service.decidirRuta(true, true, PDV, false, p));
        assertEquals(Integer.valueOf(1), service.contadorActual());
    }

    @Test
    public void ventaTicketQueRespetaLaPoliticaConTurnoFacturaYReinicia() {
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_INTERVALO, 2, true);
        service.fijarContador(0);
        assertEquals(RutaVenta.FACTURA_E_IMPRESION, service.decidirRuta(true, true, PDV, false, p));
        assertEquals(Integer.valueOf(2), service.contadorActual());
    }

    @Test
    public void ventaTicketQueNoRespetaFacturaSiempreYNoTocaElContador() {
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_A_PEDIDO, 0, false);
        service.fijarContador(5);
        assertEquals(RutaVenta.FACTURA_E_IMPRESION, service.decidirRuta(true, true, PDV, false, p));
        assertEquals(Integer.valueOf(5), service.contadorActual());
    }

    @Test
    public void ventaACreditoQuedaFueraDeRespeta() {
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_A_PEDIDO, 0, true);
        service.fijarContador(3);
        assertEquals(RutaVenta.FACTURA_E_IMPRESION, service.decidirRuta(true, true, PDV, true, p));
        assertEquals(Integer.valueOf(3), service.contadorActual());
    }

    // ── Modos ──

    @Test
    public void modoTodasFacturaCadaVentaConPuntoDeVenta() {
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_TODAS, 7, true);
        for (int i = 0; i < 3; i++) {
            assertEquals(RutaVenta.FACTURA_SILENCIOSA, service.decidirRuta(false, true, PDV, false, p));
            assertEquals(RutaVenta.FACTURA_E_IMPRESION, service.decidirRuta(true, true, PDV, false, p));
        }
        assertEquals(RutaVenta.SIN_FACTURA, service.decidirRuta(false, true, null, false, p));
        assertNull(service.contadorActual());
    }

    @Test
    public void modoAPedidoNoFacturaAutomaticamente() {
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_A_PEDIDO, 0, true);
        for (int i = 0; i < 3; i++) {
            assertEquals(RutaVenta.SIN_FACTURA, service.decidirRuta(false, true, PDV, false, p));
            assertEquals(RutaVenta.TICKET_SIMPLE, service.decidirRuta(true, true, PDV, false, p));
        }
    }

    @Test
    public void modoIntervaloFacturaUnaDeCadaNMasUno() {
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_INTERVALO, 2, false);
        List<RutaVenta> rutas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            rutas.add(service.decidirRuta(false, true, PDV, false, p));
        }
        // Primera venta tras el arranque: el contador arranca en n, como el facturaCountDown de siempre.
        assertEquals(2, rutas.stream().filter(r -> r == RutaVenta.FACTURA_SILENCIOSA).count());
        assertEquals(RutaVenta.FACTURA_SILENCIOSA, rutas.get(2));
        assertEquals(RutaVenta.FACTURA_SILENCIOSA, rutas.get(5));
    }

    @Test
    public void siBajaElIntervaloElContadorSeRecorta() {
        service.fijarContador(10);
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_INTERVALO, 1, false);
        assertEquals(RutaVenta.SIN_FACTURA, service.decidirRuta(false, true, PDV, false, p));
        assertEquals(RutaVenta.FACTURA_SILENCIOSA, service.decidirRuta(false, true, PDV, false, p));
    }

    @Test
    public void facturaManualNoTocaElContador() {
        service.fijarContador(0);
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_INTERVALO, 3, true);
        assertEquals(RutaVenta.SIN_FACTURA, service.decidirRuta(false, false, PDV, false, p));
        assertEquals(RutaVenta.SIN_FACTURA, service.decidirRuta(null, false, PDV, false, p));
        assertEquals(Integer.valueOf(0), service.contadorActual());
    }

    // ── Delivery y devolucion de turno ──

    @Test
    public void deliverySigueLaMismaBanderaQueVentaTicket() {
        assertTrue(service.facturarDelivery(PDV, politica(ConfiguracionFacturacion.MODO_A_PEDIDO, 0, false)));
        assertFalse(service.facturarDelivery(PDV, politica(ConfiguracionFacturacion.MODO_A_PEDIDO, 0, true)));
        assertTrue(service.facturarDelivery(PDV, politica(ConfiguracionFacturacion.MODO_TODAS, 0, true)));
        assertFalse(service.facturarDelivery(null, politica(ConfiguracionFacturacion.MODO_TODAS, 0, false)));
    }

    @Test
    public void devolverTurnoHaceQueLaProximaVentaFacture() {
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_INTERVALO, 4, false);
        service.fijarContador(0);
        assertEquals(RutaVenta.FACTURA_SILENCIOSA, service.decidirRuta(false, true, PDV, false, p));
        service.devolverTurno();
        assertEquals(RutaVenta.FACTURA_SILENCIOSA, service.decidirRuta(false, true, PDV, false, p));
    }

    @Test
    public void soloLaValidacionPreviaCuentaComoFallaAntesDeEscribir() {
        // Asi envuelve saveFacturaLegal cualquier falla: GraphQLException("Error al guardar...", e).
        Exception validacion = new graphql.GraphQLException("Error al guardar factura legal: sin timbrado",
                new graphql.GraphQLException("No hay timbrado vigente para el punto de venta"));
        Exception posterior = new graphql.GraphQLException("Error al guardar factura legal: boom",
                new IllegalStateException("fallo despues del insert"));
        assertTrue(PoliticaFacturacionService.fallaAntesDeEscribir(validacion));
        assertTrue(PoliticaFacturacionService.fallaAntesDeEscribir(new graphql.GraphQLException("pdvId es requerido")));
        assertFalse(PoliticaFacturacionService.fallaAntesDeEscribir(posterior));
        assertFalse(PoliticaFacturacionService.fallaAntesDeEscribir(new RuntimeException("SIFEN caido")));
    }

    @Test
    public void soloSeDevuelveElTurnoQueSeTomo() {
        PoliticaFacturacion intervaloRespeta = politica(ConfiguracionFacturacion.MODO_INTERVALO, 2, true);
        PoliticaFacturacion intervaloNoRespeta = politica(ConfiguracionFacturacion.MODO_INTERVALO, 2, false);
        PoliticaFacturacion todas = politica(ConfiguracionFacturacion.MODO_TODAS, 0, true);

        assertTrue(PoliticaFacturacionService.salioDeUnTurno(RutaVenta.FACTURA_SILENCIOSA, false, intervaloNoRespeta));
        assertTrue(PoliticaFacturacionService.salioDeUnTurno(RutaVenta.FACTURA_E_IMPRESION, false, intervaloRespeta));
        // "Venta + Ticket" que factura siempre, o una venta a credito: no tomaron turno.
        assertFalse(PoliticaFacturacionService.salioDeUnTurno(RutaVenta.FACTURA_E_IMPRESION, false, intervaloNoRespeta));
        assertFalse(PoliticaFacturacionService.salioDeUnTurno(RutaVenta.FACTURA_E_IMPRESION, true, intervaloRespeta));
        // Sin contador no hay turno.
        assertFalse(PoliticaFacturacionService.salioDeUnTurno(RutaVenta.FACTURA_SILENCIOSA, false, todas));
    }

    @Test
    public void bajoConcurrenciaNoSePierdenNiDuplicanTurnos() throws Exception {
        PoliticaFacturacion p = politica(ConfiguracionFacturacion.MODO_INTERVALO, 4, false);
        service.fijarContador(0);
        int ventas = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<RutaVenta>> resultados = new ArrayList<>();
        for (int i = 0; i < ventas; i++) {
            resultados.add(pool.submit(() -> {
                largada.await();
                return service.decidirRuta(false, true, PDV, false, p);
            }));
        }
        largada.countDown();
        int facturadas = 0;
        for (Future<RutaVenta> f : resultados) {
            if (f.get(10, TimeUnit.SECONDS) == RutaVenta.FACTURA_SILENCIOSA) facturadas++;
        }
        pool.shutdown();
        // Contador en 0 al arrancar e intervalo 4: facturan las ventas 1, 6, 11, ... → ceil(1000 / 5).
        assertEquals(200, facturadas);
    }
}
