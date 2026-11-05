package com.franco.dev.service.impresion;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.impresion.dto.GastoDto;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.impresion.dto.SenaCuponDto;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Reimpresiones del filial (#213): con {@code esperarEnvio} un ticket chico que la impresora rechaza
 * devuelve false; sin espera, el guardado (retiro, gasto, cierre) sigue como siempre.
 */
class ImpresionServiceEsperaTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 30, 10, 30);

    @Mock private ImageService imageService;
    @Mock private PrintingService printingService;
    @Mock private SucursalService sucursalService;
    @Mock private TimbradoDetalleService timbradoDetalleService;
    @InjectMocks private ImpresionService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        File dir = Files.createTempDirectory("frc-ticket").toFile();
        dir.deleteOnExit();
        imageService.storageDirectoryPath = CapturaPrintService.directorioConLogo(dir);
        Sucursal sucursal = new Sucursal();
        sucursal.setNombre("CENTRO");
        when(sucursalService.sucursalActual()).thenReturn(sucursal);
    }

    /** CUPS que lee todo el ticket y despues lo rechaza: el ticket entra en el buffer sin que write se entere. */
    private void impresoraQueRechaza() throws PrintException {
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            while (in.read() != -1) {
                // consume hasta el cierre
            }
            throw new PrintException("Printer is not accepting job.");
        }).when(job).print(any(), any());
        PrintService ps = mock(PrintService.class);
        when(ps.createPrintJob()).thenReturn(job);
        when(printingService.getPrintService(any())).thenReturn(ps);
        when(printingService.getLasUsedPrinter()).thenReturn(ps);
    }

    private CapturaPrintService impresoraQueAcepta() {
        CapturaPrintService ps = new CapturaPrintService();
        when(printingService.getPrintService(any())).thenReturn(ps);
        return ps;
    }

    private static Usuario usuario() {
        Persona p = new Persona();
        p.setNombre("CAJERO");
        Usuario u = new Usuario();
        u.setPersona(p);
        return u;
    }

    private static Funcionario funcionario() {
        Persona p = new Persona();
        p.setNombre("RESPONSABLE");
        Funcionario f = new Funcionario();
        f.setPersona(p);
        return f;
    }

    private static RetiroDto retiro() {
        RetiroDto dto = new RetiroDto();
        dto.setId(9L);
        dto.setCajaId(662L);
        dto.setFecha(FECHA);
        dto.setUsuario(usuario());
        dto.setResponsable(funcionario());
        dto.setRetiroGs(200000.0);
        dto.setRetiroRs(0.0);
        dto.setRetiroDs(0.0);
        return dto;
    }

    private static GastoDto gasto() {
        GastoDto dto = new GastoDto();
        dto.setId(15L);
        dto.setCajaId(662L);
        dto.setFecha(FECHA);
        dto.setUsuario(usuario());
        dto.setResponsable(funcionario());
        dto.setAutorizadoPor(funcionario());
        TipoGasto tipo = new TipoGasto();
        tipo.setId(3L);
        tipo.setDescripcion("LIMPIEZA");
        dto.setTipoGasto(tipo);
        dto.setRetiroGs(50000.0);
        dto.setRetiroRs(0.0);
        dto.setRetiroDs(0.0);
        dto.setReimpresion(true);
        return dto;
    }

    private static PdvCajaBalanceDto balance() {
        PdvCajaBalanceDto dto = new PdvCajaBalanceDto();
        dto.setIdCaja(662L);
        dto.setUsuario(usuario());
        dto.setFechaApertura(FECHA);
        dto.setFechaCierre(FECHA.plusHours(8));
        dto.setTotalVentaGs(1500000.0);
        return dto;
    }

    private static SenaCuponDto sena() {
        SenaCuponDto dto = new SenaCuponDto();
        dto.setVentaId(80078L);
        dto.setVentaTarjetaId(12L);
        dto.setMonto(150000.0);
        dto.setMonedaSimbolo("Gs.");
        dto.setDecimales(0);
        dto.setQr("FRC-SENA|80078|12");
        return dto;
    }

    @Test
    void conEsperaUnTicketRechazadoDevuelveFalse() throws Exception {
        impresoraQueRechaza();
        assertFalse(service.printRetiro(retiro(), "zz", "CAJA 1", true, null, true));
        assertFalse(service.printGasto(gasto(), "zz", "CAJA 1", null, true));
        assertFalse(service.printBalance(balance(), "zz", "CAJA 1", null, true));
        assertFalse(service.printSenaCupon(sena(), "zz", "CAJA 1", null, true));
    }

    @Test
    void sinEsperaUnTicketRechazadoSigueComoSiempre() throws Exception {
        // Es lo que usan saveRetiro, saveGasto y el cierre: imprimen dentro de su transaccion.
        impresoraQueRechaza();
        assertTrue(service.printRetiro(retiro(), "zz", "CAJA 1", false));
        assertTrue(service.printGasto(gasto(), "zz", "CAJA 1"));
        assertTrue(service.printBalance(balance(), "zz", "CAJA 1"));
        assertTrue(service.printSenaCupon(sena(), "zz", "CAJA 1"));
    }

    @Test
    void conEsperaUnTicketQueSaleDevuelveTrue() throws Exception {
        CapturaPrintService impresora = impresoraQueAcepta();
        assertTrue(service.printRetiro(retiro(), "ok", "CAJA 1", true, null, true));
        assertTrue(impresora.bytes().length > 0);
        assertTrue(service.printGasto(gasto(), "ok", "CAJA 1", null, true));
        assertTrue(service.printBalance(balance(), "ok", "CAJA 1", null, true));
        assertTrue(service.printSenaCupon(sena(), "ok", "CAJA 1", null, true));
    }

    @Test
    void sinImpresoraRetiroYGastoDevuelvenFalse() {
        when(printingService.getPrintService(any())).thenReturn(null);
        assertFalse(service.printRetiro(retiro(), "no-existe", "CAJA 1", true, null, true));
        assertFalse(service.printGasto(gasto(), "no-existe", "CAJA 1", null, true));
    }

    @Test
    void elBalanceDeUnaCajaAbiertaSaleConCierreAbierta() throws Exception {
        PdvCajaBalanceDto abierta = balance();
        abierta.setFechaCierre(null);
        abierta.setTotalTarjeta(null);
        abierta.setTotalRetiroRs(null);
        java.io.ByteArrayOutputStream memoria = new java.io.ByteArrayOutputStream();

        assertTrue(service.printBalance(abierta, "zz", "CAJA 1", memoria));
        assertTrue(new String(memoria.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1).contains("ABIERTA"));
    }

    @Test
    void unGastoYUnRetiroConMontosNulosSeImprimenConCero() throws Exception {
        GastoDto gasto = gasto();
        gasto.setRetiroGs(null);
        gasto.setRetiroRs(null);
        RetiroDto retiro = retiro();
        retiro.setRetiroGs(null);
        retiro.setRetiroDs(null);
        java.io.ByteArrayOutputStream memoria = new java.io.ByteArrayOutputStream();

        assertTrue(service.printGasto(gasto, "zz", "CAJA 1", memoria));
        assertTrue(service.printRetiro(retiro, "zz", "CAJA 1", false, memoria));
        assertFalse(new String(memoria.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1).contains("null"));
    }
}
