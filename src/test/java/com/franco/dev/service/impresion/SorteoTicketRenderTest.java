package com.franco.dev.service.impresion;

import com.franco.dev.service.impresion.dto.SorteoTicketDto;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SorteoTicketRenderTest {

    @Mock private ImageService imageService;
    @Mock private PrintingService printingService;
    @InjectMocks private ImpresionService service;

    private SorteoTicketDto ticket(int numero, int total, boolean reimpresion) {
        SorteoTicketDto t = new SorteoTicketDto();
        t.setNombreSorteo("Sorteo de Navidad");
        t.setDescripcion("Participa con tu compra y ganate un premio increible para estas fiestas");
        t.setPremios(Arrays.asList("Una moto 0km", "Canasta navidena con productos seleccionados de toda la tienda"));
        t.setFechaDesde(LocalDate.of(2026, 12, 1));
        t.setFechaHasta(LocalDate.of(2026, 12, 24));
        t.setFechaSorteo(LocalDate.of(2026, 12, 26));
        t.setSucursal("CENTRO");
        t.setVentaId(1234L);
        t.setFecha(LocalDateTime.of(2026, 12, 5, 10, 30));
        t.setTotalGs(250000.0);
        t.setCodigo("NAV-ABC123");
        t.setNumero(numero);
        t.setTotal(total);
        t.setClienteNombre("Juan Perez");
        t.setClienteDocumento("1234567-8");
        t.setReimpresion(reimpresion);
        return t;
    }

    private String render(boolean conLogo, List<SorteoTicketDto> tickets, ByteArrayOutputStream out) throws Exception {
        MockitoAnnotations.initMocks(this);
        File dir = Files.createTempDirectory("frc-sorteo").toFile();
        dir.deleteOnExit();
        imageService.storageDirectoryPath = conLogo ? CapturaPrintService.directorioConLogo(dir)
                : dir.getAbsolutePath() + File.separator;
        assertTrue(service.printSorteoTickets(tickets, "X", "L", out));
        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static int contar(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    @Test
    void dosTicketsConCortesYContenido() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String s = render(true, Arrays.asList(ticket(1, 2, false), ticket(2, 2, false)), out);
        // El cliente no ve el número de ticket ni el código; sí la venta, que sirve para validar.
        assertFalse(s.contains("Ticket 1 de 2"));
        assertFalse(s.contains("NAV-ABC123"));
        assertTrue(s.contains("Venta: 1234"));
        assertTrue(s.contains("Firma:"));
        assertTrue(s.contains("Juan Perez"));
        assertTrue(s.contains("Deposite este ticket firmado en la urna"));
        assertTrue(s.contains("250.000"));
        assertTrue(s.contains("Sorteo: 26-12-2026"));
        assertFalse(s.contains("Vigencia"));
        assertFalse(s.contains("REIMPRESION"));
        assertEquals(2, contar(s, "\u001dV0"));
    }

    @Test
    void sinLogoNoFallaYReimpresionLoMarca() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String s = render(false, Arrays.asList(ticket(1, 1, true)), out);
        assertTrue(s.contains("REIMPRESION"));
        assertEquals(1, contar(s, "\u001dV0"));
    }

    @Test
    void sinDatosDelClienteSaleConLineasParaCompletarAMano() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SorteoTicketDto t = ticket(1, 1, false);
        t.setClienteNombre(null);
        t.setClienteDocumento(null);
        String s = render(false, Arrays.asList(t), out);
        assertTrue(s.contains("Nombre: ______________"));
        assertTrue(s.contains("CI/RUC: ______________"));
        assertTrue(s.contains("Firma: ______________"));
    }

    @Test
    void conDatosDelClienteNoImprimeLineasEnBlanco() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String s = render(false, Arrays.asList(ticket(1, 1, false)), out);
        assertFalse(s.contains("Nombre: ______________"));
        assertFalse(s.contains("CI/RUC: ______________"));
        assertTrue(s.contains("CI/RUC: 1234567-8"));
    }

    /** Saca los comandos ESC/GS que genera Style/EscPos para dejar solo el texto imprimible. */
    private static String soloTexto(String s) {
        return s.replaceAll("\u001dV[\\s\\S]", "")              // GS V n (corte)
                .replaceAll("\u001d[!B][\\s\\S]", "")           // GS ! n, GS B n
                .replaceAll("\u001b[MEa\\-3dt][\\s\\S]", "")     // ESC M/E/a/-/3/d/t n
                .replaceAll("\u001b2", "");                    // ESC 2
    }

    @Test
    void ningunaLineaPasaDe32Columnas() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String s = soloTexto(render(false, Arrays.asList(ticket(1, 1, false)), out));
        assertTrue(s.contains("Canasta"));
        int medidas = 0;
        for (String linea : s.split("\n")) {
            int max = linea.contains("Deposite") ? 42 : 32; // el pie va en fuente B
            assertTrue(linea.length() <= max, "linea larga (" + linea.length() + "): " + linea);
            medidas++;
        }
        assertTrue(medidas > 15);
    }

    @Test
    void nuncaLanza() {
        MockitoAnnotations.initMocks(this);
        assertFalse(service.printSorteoTickets(null, "X", "L", new ByteArrayOutputStream()));
    }

    /** Modo FRONTEND: si un ticket revienta a mitad, al comprobante no se le pega nada. */
    @Test
    void errorAMitadNoAgregaBytesAlComprobante() throws Exception {
        MockitoAnnotations.initMocks(this);
        File dir = Files.createTempDirectory("frc-sorteo").toFile();
        dir.deleteOnExit();
        imageService.storageDirectoryPath = dir.getAbsolutePath() + File.separator;
        SorteoTicketDto roto = new SorteoTicketDto() {
            @Override
            public String getClienteNombre() {
                throw new IllegalStateException("boom");
            }
        };
        roto.setNombreSorteo("Sorteo");
        roto.setNumero(2);
        roto.setTotal(2);
        ByteArrayOutputStream comprobante = new ByteArrayOutputStream();
        comprobante.write("RECIBO".getBytes(StandardCharsets.ISO_8859_1));

        assertFalse(service.printSorteoTickets(Arrays.asList(ticket(1, 2, false), roto), "X", "L", comprobante));

        assertEquals("RECIBO", new String(comprobante.toByteArray(), StandardCharsets.ISO_8859_1));
    }
}
