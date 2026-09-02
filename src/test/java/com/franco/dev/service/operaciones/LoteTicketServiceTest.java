package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.LoteVendidoDto;
import com.franco.dev.repository.operaciones.MovimientoStockLoteRepository;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El ancho de 32 columnas no es negociable: si una línea se pasa, la impresora la corta sola y el
 * vencimiento se pierde justo en el ticket donde importaba.
 */
class LoteTicketServiceTest {

    private static final long ITEM = 77L;
    private static final long SUCURSAL = 3L;

    private LoteTicketService servicioCon(List<LoteVendidoDto> filas) {
        MovimientoStockLoteRepository repo = mock(MovimientoStockLoteRepository.class);
        when(repo.lotesVendidosPorItem(anyList(), anyLong())).thenReturn(filas);
        return new LoteTicketService(repo);
    }

    private LoteVendidoDto fila(String numero, LocalDate vencimiento, double cantidad) {
        return new LoteVendidoDto(ITEM, numero, vencimiento, cantidad);
    }

    private List<String> lineasDe(List<LoteVendidoDto> filas) {
        Map<Long, List<String>> mapa =
                servicioCon(filas).lineasPorItem(Collections.singletonList(ITEM), SUCURSAL);
        return mapa.containsKey(ITEM) ? mapa.get(ITEM) : new ArrayList<String>();
    }

    @Test
    void unLoteConVencimientoAlineaLaFechaContraElBordeDerecho() {
        List<String> lineas = lineasDe(Collections.singletonList(
                fila("L2409A", LocalDate.of(2026, 9, 30), -1.0)));

        assertEquals(1, lineas.size());
        assertEquals(" Lote: L2409A     Vto 30/09/2026", lineas.get(0));
        assertEquals(32, lineas.get(0).length());
    }

    @Test
    void conUnSoloLoteNoSeRepiteLaCantidadQueYaEstaArriba() {
        List<String> lineas = lineasDe(Collections.singletonList(
                fila("L2409A", LocalDate.of(2026, 9, 30), -3.0)));

        assertFalse(lineas.get(0).contains("x3"));
    }

    @Test
    void cuandoFefoParteElItemCadaLoteMuestraSuCantidad() {
        List<String> lineas = lineasDe(Arrays.asList(
                fila("A2409", LocalDate.of(2026, 9, 30), -2.0),
                fila("B2410", LocalDate.of(2026, 10, 31), -1.0)));

        assertEquals(2, lineas.size());
        assertTrue(lineas.get(0).contains("A2409 x2"), lineas.get(0));
        assertTrue(lineas.get(1).contains("B2410 x1"), lineas.get(1));
        for (String linea : lineas) {
            assertTrue(linea.length() <= 32, "se pasa de 32: '" + linea + "'");
        }
    }

    @Test
    void sinVencimientoLaLineaQuedaSoloConElNumero() {
        List<String> lineas = lineasDe(Collections.singletonList(fila("L2409A", null, -1.0)));

        assertEquals(" Lote: L2409A", lineas.get(0));
    }

    /** Ver {@code LoteFefoService.NUMERO_LOTE_SIN_TRAZAR}: no es un lote, es el stock sin atribuir. */
    @Test
    void elBucketSinTrazarNoSeImprime() {
        assertTrue(lineasDe(Collections.singletonList(
                fila(LoteFefoService.NUMERO_LOTE_SIN_TRAZAR, null, -1.0))).isEmpty());
    }

    @Test
    void unLoteRealConviventeConSinTrazarImprimeSoloElReal() {
        List<String> lineas = lineasDe(Arrays.asList(
                fila("A2409", LocalDate.of(2026, 9, 30), -2.0),
                fila(LoteFefoService.NUMERO_LOTE_SIN_TRAZAR, null, -1.0)));

        assertEquals(1, lineas.size());
        assertTrue(lineas.get(0).contains("A2409"));
    }

    @Test
    void unNumeroLargoSeRecortaPeroElVencimientoSobrevive() {
        List<String> lineas = lineasDe(Collections.singletonList(
                fila("LOTE-SUPER-LARGO-QUE-NO-ENTRA", LocalDate.of(2026, 9, 30), -1.0)));

        String linea = lineas.get(0);
        assertEquals(32, linea.length());
        assertTrue(linea.endsWith("Vto 30/09/2026"), linea);
    }

    @Test
    void lasCantidadesConDecimalUsanLaComaDelRestoDelTicket() {
        List<String> lineas = lineasDe(Arrays.asList(
                fila("A", LocalDate.of(2026, 9, 30), -1.5),
                fila("B", LocalDate.of(2026, 10, 31), -0.5)));

        assertTrue(lineas.get(0).contains("x1,5"), lineas.get(0));
    }

    /** Ver el javadoc de la clase: el lote no vale una venta sin ticket. */
    @Test
    void siLaConsultaFallaElTicketSaleSinLineasDeLote() {
        MovimientoStockLoteRepository repo = mock(MovimientoStockLoteRepository.class);
        when(repo.lotesVendidosPorItem(anyList(), anyLong()))
                .thenThrow(new RuntimeException("base caida"));

        Map<Long, List<String>> mapa = new LoteTicketService(repo)
                .lineasPorItem(Collections.singletonList(ITEM), SUCURSAL);

        assertTrue(mapa.isEmpty());
    }

    /**
     * La impresora de las sucursales no rinde {@code ESC E} sola: hacen falta las dos formas de
     * resaltado. Si alguien saca una, la linea vuelve a salir como texto normal y en papel no se
     * nota hasta que alguien mira un ticket.
     */
    @Test
    void laLineaSaleConEnfatizadoYDobleGolpeYLosApagaDespues() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        EscPos escpos = new EscPos(salida);
        LoteTicketService servicio = servicioCon(Collections.<LoteVendidoDto>emptyList());

        escpos.writeLF("PILSEN CLASICA LATA 269 ML");
        servicio.escribir(escpos, Collections.singletonList(" Lote: L2409A     Vto 30/09/2026"));
        escpos.writeLF("COCA COLA 2L");

        assertEquals("010", secuencia(salida.toByteArray(), 'E'), "ESC E (enfatizado)");
        assertEquals("10", secuencia(salida.toByteArray(), 'G'), "ESC G (doble golpe)");
    }

    @Test
    void sinLineasNoEscribeNadaNiSeRompeConNull() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        LoteTicketService servicio = servicioCon(Collections.<LoteVendidoDto>emptyList());

        servicio.escribir(new EscPos(salida), null);

        assertEquals(0, salida.toByteArray().length);
    }

    /** Los valores de cada comando {@code ESC <cmd> n} que aparecen en el stream, en orden. */
    private String secuencia(byte[] bytes, char comando) {
        StringBuilder encontrados = new StringBuilder();
        for (int i = 0; i < bytes.length - 2; i++) {
            if (bytes[i] == 0x1B && bytes[i + 1] == comando) {
                encontrados.append(bytes[i + 2]);
            }
        }
        return encontrados.toString();
    }

    @Test
    void sinSucursalNoSeConsulta() {
        assertTrue(servicioCon(Collections.<LoteVendidoDto>emptyList())
                .lineasPorItem(Collections.singletonList(ITEM), null).isEmpty());
    }
}
