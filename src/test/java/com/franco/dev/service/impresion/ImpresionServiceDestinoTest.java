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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Impresion desde el cliente: cada ticket del POS escrito en un stream en memoria tiene que ser,
 * byte por byte, el mismo que hoy le llega a la impresora por el camino del servidor. Y el camino
 * del cliente no busca ninguna impresora: en la PC del filial puede no haber ninguna.
 */
class ImpresionServiceDestinoTest {

    @Mock private ImageService imageService;
    @Mock private PrintingService printingService;
    @Mock private SucursalService sucursalService;
    @Mock private TimbradoDetalleService timbradoDetalleService;
    @InjectMocks private ImpresionService service;

    private CapturaPrintService impresora;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        File dir = Files.createTempDirectory("frc-ticket").toFile();
        dir.deleteOnExit();
        imageService.storageDirectoryPath = CapturaPrintService.directorioConLogo(dir);
        Sucursal sucursal = new Sucursal();
        sucursal.setNombre("CENTRO");
        when(sucursalService.sucursalActual()).thenReturn(sucursal);
        impresora = new CapturaPrintService();
        when(printingService.getPrintService(any())).thenReturn(impresora);
        when(printingService.getLasUsedPrinter()).thenReturn(impresora);
    }

    private static Usuario usuario(String nombre) {
        Persona p = new Persona();
        p.setNombre(nombre);
        Usuario u = new Usuario();
        u.setPersona(p);
        return u;
    }

    private static Funcionario funcionario(String nombre) {
        Persona p = new Persona();
        p.setNombre(nombre);
        Funcionario f = new Funcionario();
        f.setPersona(p);
        return f;
    }

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 9, 29, 10, 30);

    @Test
    void balanceEnMemoriaIgualAlImpreso() throws Exception {
        PdvCajaBalanceDto dto = new PdvCajaBalanceDto();
        dto.setIdCaja(662L);
        dto.setUsuario(usuario("CAJERO DE PRUEBA CON UN NOMBRE MUY LARGO"));
        dto.setFechaApertura(FECHA);
        dto.setFechaCierre(FECHA.plusHours(8));
        dto.setTotalVentaGs(1500000.0);

        assertTrue(service.printBalance(dto, "ticket", "CAJA 1"));
        byte[] impreso = impresora.bytes();

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        reset(printingService);
        assertTrue(service.printBalance(dto, "ticket", "CAJA 1", memoria));

        assertTrue(impreso.length > 0);
        assertArrayEquals(impreso, memoria.toByteArray());
        verifyZeroInteractions(printingService);
    }

    @Test
    void gastoEnMemoriaIgualAlImpreso() throws Exception {
        GastoDto dto = new GastoDto();
        dto.setId(15L);
        dto.setCajaId(662L);
        dto.setFecha(FECHA);
        dto.setUsuario(usuario("CAJERO"));
        dto.setResponsable(funcionario("RESPONSABLE"));
        dto.setAutorizadoPor(funcionario("AUTORIZANTE"));
        TipoGasto tipo = new TipoGasto();
        tipo.setId(3L);
        tipo.setDescripcion("LIMPIEZA");
        dto.setTipoGasto(tipo);
        dto.setObservacion("detergente");
        dto.setRetiroGs(50000.0);
        dto.setRetiroRs(0.0);
        dto.setRetiroDs(0.0);
        dto.setReimpresion(true);

        service.printGasto(dto, "ticket", "CAJA 1");
        byte[] impreso = impresora.bytes();

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        reset(printingService);
        service.printGasto(dto, "ticket", "CAJA 1", memoria);

        assertTrue(impreso.length > 0);
        assertArrayEquals(impreso, memoria.toByteArray());
        verifyZeroInteractions(printingService);
    }

    @Test
    void retiroEnMemoriaIgualAlImpreso() throws Exception {
        RetiroDto dto = new RetiroDto();
        dto.setId(9L);
        dto.setCajaId(662L);
        dto.setFecha(FECHA);
        dto.setUsuario(usuario("CAJERO"));
        dto.setResponsable(funcionario("RESPONSABLE"));
        dto.setRetiroGs(200000.0);
        dto.setRetiroRs(10.0);
        dto.setRetiroDs(0.0);

        service.printRetiro(dto, "ticket", "CAJA 1", false);
        byte[] impreso = impresora.bytes();

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        reset(printingService);
        service.printRetiro(dto, "ticket", "CAJA 1", false, memoria);

        assertTrue(impreso.length > 0);
        assertArrayEquals(impreso, memoria.toByteArray());
        verifyZeroInteractions(printingService);
    }

    @Test
    void senaCuponEnMemoriaIgualAlImpreso() throws Exception {
        SenaCuponDto dto = new SenaCuponDto();
        dto.setVentaId(80078L);
        dto.setVentaTarjetaId(12L);
        dto.setMonto(150000.0);
        dto.setMonedaSimbolo("Gs.");
        dto.setDecimales(0);
        dto.setQr("FRC-SENA|80078|12");

        // El pie lleva la hora actual (dd-MM HH:mm): las dos impresiones van en el mismo minuto.
        assertTrue(service.printSenaCupon(dto, "ticket", "CAJA 1"));
        byte[] impreso = impresora.bytes();

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        reset(printingService);
        assertTrue(service.printSenaCupon(dto, "ticket", "CAJA 1", memoria));

        assertTrue(impreso.length > 0);
        assertArrayEquals(impreso, memoria.toByteArray());
        verifyZeroInteractions(printingService);
    }
}
