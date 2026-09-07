package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import com.franco.dev.repository.operaciones.CobroDetalleRepository;
import com.franco.dev.service.financiero.MonedaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VentaTarjetaServiceTest {

    private VentaTarjetaRepository repository;

    private CobroDetalleRepository cobroDetalleRepository;

    private MonedaService monedaService;

    private VentaTarjetaService service;

    @BeforeEach
    void setUp() {
        repository = mock(VentaTarjetaRepository.class);
        cobroDetalleRepository = mock(CobroDetalleRepository.class);
        monedaService = mock(MonedaService.class);
        service = new VentaTarjetaService(repository, cobroDetalleRepository, monedaService);
    }

    @Test
    void marcarNoCompletadas_pasaPendientesANoCompletadoYDevuelveCantidad() {
        VentaTarjeta vt1 = new VentaTarjeta();
        vt1.setEstado("PENDIENTE");
        VentaTarjeta vt2 = new VentaTarjeta();
        vt2.setEstado("PENDIENTE");
        when(repository.findByCajaIdAndSucursalIdAndEstado(10L, 1L, "PENDIENTE"))
                .thenReturn(Arrays.asList(vt1, vt2));

        int cantidad = service.marcarNoCompletadas(10L, 1L);

        assertEquals(2, cantidad);
        assertEquals("NO_COMPLETADO", vt1.getEstado());
        assertEquals("NO_COMPLETADO", vt2.getEstado());
        verify(repository, times(2)).save(any(VentaTarjeta.class));
    }

    @Test
    void marcarNoCompletadas_sinPendientesDevuelveCeroYNoGuarda() {
        when(repository.findByCajaIdAndSucursalIdAndEstado(10L, 1L, "PENDIENTE"))
                .thenReturn(Collections.emptyList());

        assertEquals(0, service.marcarNoCompletadas(10L, 1L));
        verify(repository, never()).save(any());
    }

    // ── completar: leer el QR del cupón en el PDV ──────────────────────────────────────────

    @Test
    void completar_pendiente_pasaACompletadoYCopiaLosCamposDelCupon() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setEstado("PENDIENTE");
        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);
        when(repository.save(any(VentaTarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        VentaTarjeta resultado = service.completar(
                1L, 24L, "CXF1", "", new BigDecimal("94.55"), "E60701190202608271700DY5BCKNPMBQ", "FRCP1*...", null, null);

        assertEquals("COMPLETADO", resultado.getEstado());
        assertEquals("CXF1", resultado.getCodigoAutorizacion());
        assertEquals(new BigDecimal("94.55"), resultado.getMontoEscaneado());
        assertEquals("FRCP1*...", resultado.getQrCrudo());
    }

    // Sin esta validación, escanear dos veces el mismo cupón sobreescribiría en silencio un
    // registro ya COMPLETADO (o reviviría uno CANCELADO / NO_COMPLETADO).
    @Test
    void completar_yaCompletado_rechaza() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setEstado("COMPLETADO");
        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);

        assertThrows(GraphQLException.class, () ->
                service.completar(1L, 24L, "CXF1", "", BigDecimal.TEN, "REF", "cruda", null, null));
        verify(repository, never()).save(any());
    }

    @Test
    void completar_idInexistente_rechaza() {
        when(repository.findByIdAndSucursalId(99L, 24L)).thenReturn(null);

        assertThrows(GraphQLException.class, () ->
                service.completar(99L, 24L, "CXF1", "", BigDecimal.TEN, "REF", "cruda", null, null));
        verify(repository, never()).save(any());
    }

    // El registro se completa igual aunque no se pueda vincular el identificador al cobro: el
    // dato del cajero (que la tarjeta se cobró) no puede depender de la conciliación contable.
    @Test
    void completar_sinIdentificadorTransaccion_noTocaElCobro() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setEstado("PENDIENTE");
        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);
        when(repository.save(any(VentaTarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        VentaTarjeta resultado = service.completar(1L, 24L, "CXF1", "", BigDecimal.TEN, null, "cruda", null, null);

        assertEquals("COMPLETADO", resultado.getEstado());
        verify(cobroDetalleRepository, never()).findByVentaIdAndSucursalId(any(), any());
    }

    // Dos CobroDetalle de TARJETA del mismo monto: no hay forma de saber cuál corresponde, y
    // colgarle la referencia al equivocado es peor que no vincularla.
    @Test
    void completar_dosCandidatosDelMismoMonto_noVinculaNinguno() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setMonto(new BigDecimal("2000"));
        vt.setEstado("PENDIENTE");
        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);
        when(repository.save(any(VentaTarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        FormaPago tarjeta = new FormaPago();
        tarjeta.setDescripcion("TARJETA");
        CobroDetalle cd1 = new CobroDetalle();
        cd1.setFormaPago(tarjeta);
        cd1.setPago(true);
        cd1.setValor(2000.0);
        CobroDetalle cd2 = new CobroDetalle();
        cd2.setFormaPago(tarjeta);
        cd2.setPago(true);
        cd2.setValor(2000.0);
        when(cobroDetalleRepository.findByVentaIdAndSucursalId(500L, 24L))
                .thenReturn(Arrays.asList(cd1, cd2));

        service.completar(1L, 24L, "CXF1", "", BigDecimal.TEN, "REF-UNICA", "cruda", null, null);

        verify(cobroDetalleRepository, never()).save(any(CobroDetalle.class));
    }

    @Test
    void completar_unSoloCandidato_vinculaElIdentificador() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setEstado("PENDIENTE");
        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);
        when(repository.save(any(VentaTarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        FormaPago tarjeta = new FormaPago();
        tarjeta.setDescripcion("TARJETA");
        CobroDetalle cd = new CobroDetalle();
        cd.setFormaPago(tarjeta);
        cd.setPago(true);
        cd.setValor(2000.0);
        when(cobroDetalleRepository.findByVentaIdAndSucursalId(500L, 24L))
                .thenReturn(Collections.singletonList(cd));

        service.completar(1L, 24L, "CXF1", "", BigDecimal.TEN, "REF-UNICA", "cruda", null, null);

        verify(cobroDetalleRepository).save(cd);
        assertEquals("REF-UNICA", cd.getIdentificadorTransaccion());
    }

    /** Arma una venta con dos cobros con tarjeta del MISMO monto: el caso que la inferencia
     *  no puede desempatar, y que por eso obliga a elegir. */
    private CobroDetalle[] dosTarjetasDelMismoMonto(VentaTarjeta vt) {
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setMonto(new BigDecimal("4000"));
        vt.setEstado("PENDIENTE");
        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);
        when(repository.save(any(VentaTarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        FormaPago tarjeta = new FormaPago();
        tarjeta.setDescripcion("TARJETA");
        CobroDetalle cd1 = new CobroDetalle();
        cd1.setId(10L);
        cd1.setFormaPago(tarjeta);
        cd1.setPago(true);
        cd1.setValor(4000.0);
        CobroDetalle cd2 = new CobroDetalle();
        cd2.setId(11L);
        cd2.setFormaPago(tarjeta);
        cd2.setPago(true);
        cd2.setValor(4000.0);
        when(cobroDetalleRepository.findByVentaIdAndSucursalId(500L, 24L))
                .thenReturn(Arrays.asList(cd1, cd2));
        return new CobroDetalle[]{cd1, cd2};
    }

    // El usuario eligio la linea: es el unico camino que desempata dos cobros de igual monto.
    @Test
    void completar_conCobroDetalleIdExplicito_vinculaEsaLineaYNoLaOtra() {
        VentaTarjeta vt = new VentaTarjeta();
        CobroDetalle[] cds = dosTarjetasDelMismoMonto(vt);

        service.completar(1L, 24L, "CXF1", "", BigDecimal.TEN, "REF-ELEGIDA", "cruda", 11L, null);

        assertEquals("REF-ELEGIDA", cds[1].getIdentificadorTransaccion());
        assertNull(cds[0].getIdentificadorTransaccion());
        verify(cobroDetalleRepository).save(cds[1]);
    }

    // Elegir un cobro que no es de la venta tiene que fallar hacia afuera, no quedar en un warn:
    // si no, el cajero cree que vinculo algo que no vinculo.
    @Test
    void completar_conCobroDetalleIdAjeno_falla() {
        VentaTarjeta vt = new VentaTarjeta();
        dosTarjetasDelMismoMonto(vt);

        assertThrows(GraphQLException.class, () ->
                service.completar(1L, 24L, "CXF1", "", BigDecimal.TEN, "REF", "cruda", 999L, null));
    }

    @Test
    void completar_conCobroDetalleYaTomadoPorOtroCupon_falla() {
        VentaTarjeta vt = new VentaTarjeta();
        CobroDetalle[] cds = dosTarjetasDelMismoMonto(vt);
        cds[0].setIdentificadorTransaccion("REF-DE-OTRO-CUPON");

        assertThrows(GraphQLException.class, () ->
                service.completar(1L, 24L, "CXF1", "", BigDecimal.TEN, "REF-NUEVA", "cruda", 10L, null));
    }

    // 8.000 R$ contra un cobro de 8.000 Gs da diferencia CERO en cualquier reporte: el error
    // queda invisible y son ~5900x. Verificado en la prueba manual del 2026-09-04, donde se
    // registro sin un solo aviso.
    @Test
    void completar_conCuponEnOtraMonedaQueLaTerminal_falla() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setEstado("PENDIENTE");

        Moneda real = new Moneda();
        real.setId(2L);
        TerminalPos terminal = new TerminalPos();
        terminal.setMoneda(real);
        vt.setTerminalPos(terminal);

        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);

        // El cupon viene en GUARANI (1) y la terminal cobra en REAL (2).
        assertThrows(GraphQLException.class, () ->
                service.completar(1L, 24L, "X", "Y", BigDecimal.TEN, "REF", "cruda", null, 1L));

        verify(repository, never()).save(any(VentaTarjeta.class));
    }

    @Test
    void completar_conCuponEnLaMismaMoneda_pasa() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setEstado("PENDIENTE");

        Moneda real = new Moneda();
        real.setId(2L);
        TerminalPos terminal = new TerminalPos();
        terminal.setMoneda(real);
        vt.setTerminalPos(terminal);

        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);
        when(repository.save(any(VentaTarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        VentaTarjeta r = service.completar(1L, 24L, "X", "Y", BigDecimal.TEN, "REF", "cruda", null, 2L);

        assertEquals("COMPLETADO", r.getEstado());
    }

    // Sin moneda en el cupon (formatos que no la declaran) no hay nada que comparar: no puede
    // bloquear, o esos proveedores dejarian de poder registrarse.
    @Test
    void completar_sinMonedaEnElCupon_noBloquea() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setEstado("PENDIENTE");

        Moneda real = new Moneda();
        real.setId(2L);
        TerminalPos terminal = new TerminalPos();
        terminal.setMoneda(real);
        vt.setTerminalPos(terminal);

        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);
        when(repository.save(any(VentaTarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        VentaTarjeta r = service.completar(1L, 24L, "X", "Y", BigDecimal.TEN, "REF", "cruda", null, null);

        assertEquals("COMPLETADO", r.getEstado());
    }

    // Un cupon del POS corresponde a UN cobro. Registrarlo dos veces imputa la misma plata a dos
    // ventas: descuadre real de caja. Paso en la prueba manual del 2026-09-04 (vt 18 y 19 con el
    // mismo qr_crudo), y el sistema lo acepto con un simple aviso amarillo.
    @Test
    void completar_conCuponYaRegistradoEnOtraVenta_falla() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setEstado("PENDIENTE");
        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);

        VentaTarjeta otro = new VentaTarjeta();
        otro.setId(99L);
        otro.setVentaId(400L);
        when(repository.findByQrCrudo("FRCP1*X*Y*PYG*5000*REF*202609041100"))
                .thenReturn(Collections.singletonList(otro));

        assertThrows(GraphQLException.class, () ->
                service.completar(1L, 24L, "X", "Y", BigDecimal.TEN, "REF",
                        "FRCP1*X*Y*PYG*5000*REF*202609041100", null, null));

        // Y no se escribio nada: el registro sigue como estaba.
        verify(repository, never()).save(any(VentaTarjeta.class));
    }

    // El mismo cupon puede llegar con otra cadena cruda; la referencia del proveedor es la que
    // identifica la transaccion de verdad.
    @Test
    void completar_conIdentificadorYaUsadoEnOtraVenta_falla() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setEstado("PENDIENTE");
        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);

        CobroDetalle ajeno = new CobroDetalle();
        ajeno.setId(777L);
        when(cobroDetalleRepository.findByIdentificadorTransaccion("REF-USADA"))
                .thenReturn(Collections.singletonList(ajeno));
        when(cobroDetalleRepository.findByVentaIdAndSucursalId(500L, 24L))
                .thenReturn(Collections.emptyList());

        assertThrows(GraphQLException.class, () ->
                service.completar(1L, 24L, "X", "Y", BigDecimal.TEN, "REF-USADA", "cruda", null, null));

        verify(repository, never()).save(any(VentaTarjeta.class));
    }

    // La misma referencia en un cobro de ESTA venta no es un duplicado: es el vinculo que el PDV
    // ya dejo escrito al guardar. Si esto fallara, el camino normal del PDV quedaria roto.
    @Test
    void completar_conIdentificadorEnUnCobroDeLaMismaVenta_noFalla() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setSucursalId(24L);
        vt.setVentaId(500L);
        vt.setEstado("PENDIENTE");
        when(repository.findByIdAndSucursalId(1L, 24L)).thenReturn(vt);
        when(repository.save(any(VentaTarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        CobroDetalle propio = new CobroDetalle();
        propio.setId(10L);
        propio.setIdentificadorTransaccion("REF-PROPIA");
        FormaPago tarjeta = new FormaPago();
        tarjeta.setDescripcion("TARJETA");
        propio.setFormaPago(tarjeta);
        propio.setPago(true);
        propio.setValor(4000.0);

        when(cobroDetalleRepository.findByIdentificadorTransaccion("REF-PROPIA"))
                .thenReturn(Collections.singletonList(propio));
        when(cobroDetalleRepository.findByVentaIdAndSucursalId(500L, 24L))
                .thenReturn(Collections.singletonList(propio));

        VentaTarjeta r = service.completar(1L, 24L, "X", "Y", BigDecimal.TEN, "REF-PROPIA", "cruda", null, null);

        assertEquals("COMPLETADO", r.getEstado());
    }

    // El PDV ya mando el identificador en el CobroDetalleInput al guardar la venta. Sin este
    // corte, la inferencia le colgaria el mismo identificador a la OTRA linea del mismo monto.
    @Test
    void completar_cuandoElIdentificadorYaEstaVinculado_noTocaLaOtraLinea() {
        VentaTarjeta vt = new VentaTarjeta();
        CobroDetalle[] cds = dosTarjetasDelMismoMonto(vt);
        cds[0].setIdentificadorTransaccion("REF-YA-PUESTA");

        service.completar(1L, 24L, "CXF1", "", BigDecimal.TEN, "REF-YA-PUESTA", "cruda", null, null);

        assertNull(cds[1].getIdentificadorTransaccion());
        verify(cobroDetalleRepository, never()).save(any(CobroDetalle.class));
    }
}
