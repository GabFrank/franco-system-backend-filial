package com.franco.dev.service.impresion;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.barcode.BarCodeWrapperInterface;
import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** QR de los tickets de retiro y de cierre para el lector de caja mayor. */
class ImpresionServiceQrTest {

    @Mock private ImageService imageService;
    @Mock private PrintingService printingService;
    @Mock private SucursalService sucursalService;
    @Mock private TimbradoDetalleService timbradoDetalleService;
    @InjectMocks private ImpresionService service;

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 3, 10, 30);

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

    private static RetiroDto retiro(Long sucursalId) {
        RetiroDto dto = new RetiroDto();
        dto.setId(1151L);
        dto.setCajaId(662L);
        dto.setSucursalId(sucursalId);
        dto.setFecha(FECHA);
        dto.setUsuario(usuario());
        dto.setResponsable(funcionario());
        dto.setRetiroGs(200000.0);
        dto.setRetiroRs(10.0);
        dto.setRetiroDs(0.0);
        return dto;
    }

    private static String texto(ByteArrayOutputStream out) {
        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    @Test
    void ticketDeRetiroLlevaElQrConCajaYSucursal() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(service.printRetiro(retiro(24L), null, "CAJA 1", false, out));
        assertTrue(texto(out).contains("frc-24-RETIRO-1151-24-IngresarRetiroCajaMayor-662-null"));
    }

    @Test
    void retiroSinSucursalSaleSinQr() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(service.printRetiro(retiro(null), null, "CAJA 1", false, out));
        assertFalse(texto(out).contains("frc-"));
        assertTrue(texto(out).contains("FIRMA RESPONSABLE"));
    }

    @Test
    void ticketDeCierreLlevaElQrDeLaCaja() {
        PdvCajaBalanceDto dto = new PdvCajaBalanceDto();
        dto.setIdCaja(662L);
        Sucursal suc = new Sucursal();
        suc.setId(24L);
        suc.setNombre("CENTRO");
        dto.setSucursal(suc);
        dto.setUsuario(usuario());
        dto.setFechaApertura(FECHA);
        dto.setFechaCierre(FECHA.plusHours(8));
        dto.setTotalVentaGs(1500000.0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(service.printBalance(dto, null, "CAJA 1", out));
        assertTrue(texto(out).contains("frc-24-PDVCAJA-662-24-IngresarRetiroCajaMayor-null-null"));
    }

    /** saveRetiro imprime dentro de su transaccion: una excepcion del QR desharia el retiro. */
    @Test
    void unQrQueFallaNoCortaElTicket() throws Exception {
        EscPos escpos = mock(EscPos.class);
        doThrow(new IllegalStateException("QR roto")).when(escpos).write(any(BarCodeWrapperInterface.class), anyString());
        assertDoesNotThrow(() -> ImpresionService.escribirQr(escpos, "frc-24-PDVCAJA-662-24-x-null-null"));
    }
}
