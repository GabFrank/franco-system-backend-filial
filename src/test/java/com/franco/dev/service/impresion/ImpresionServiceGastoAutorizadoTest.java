package com.franco.dev.service.impresion;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.impresion.dto.GastoDto;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Ticket de gasto: "Autorizado por" debajo del cajero cuando el gasto tiene autorizacion. */
class ImpresionServiceGastoAutorizadoTest {

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
        sucursal.setNombre("CALLE 10");
        when(sucursalService.sucursalActual()).thenReturn(sucursal);
    }

    private static Persona persona(String nombre) {
        Persona p = new Persona();
        p.setNombre(nombre);
        return p;
    }

    private static Funcionario funcionario(String nombre) {
        Funcionario f = new Funcionario();
        f.setPersona(persona(nombre));
        return f;
    }

    private static GastoDto gasto() {
        Usuario u = new Usuario();
        u.setPersona(persona("DIEGO PAULINHO AMARILLA"));
        GastoDto dto = new GastoDto();
        dto.setId(116L);
        dto.setCajaId(3022L);
        dto.setFecha(LocalDateTime.of(2026, 11, 24, 11, 24));
        dto.setUsuario(u);
        dto.setResponsable(funcionario("ANDREA BEATRIZ DUARTE R"));
        dto.setRetiroGs(22000.0);
        dto.setRetiroRs(0.0);
        dto.setRetiroDs(0.0);
        dto.setObservacion("PRUEBA");
        return dto;
    }

    private String imprimir(GastoDto dto) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(service.printGasto(dto, null, "Caja 1", out));
        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    @Test
    void gastoDeSolicitudAutorizadaLlevaAutorizadoPorDebajoDelCajero() {
        GastoDto dto = gasto();
        dto.setAutorizadoPorSolicitud("Juan Perez");
        String t = imprimir(dto);
        int cajero = t.indexOf("Cajero: DIEGO PAULINHO AMARILLA");
        int autorizado = t.indexOf("Autorizado por: JUAN PEREZ");
        int fecha = t.indexOf("Fecha 24-11-2026 11:24");
        assertTrue(cajero >= 0 && autorizado > cajero && fecha > autorizado, t);
    }

    @Test
    void nombreLargoVaEnLaLineaSiguiente() {
        GastoDto dto = gasto();
        dto.setAutorizadoPorSolicitud("MARIA FERNANDA GONZALEZ BENITEZ");
        String t = imprimir(dto);
        int titulo = t.indexOf("Autorizado por:\n");
        int nombre = t.indexOf("MARIA FERNANDA GONZALEZ BENITEZ");
        assertTrue(titulo >= 0 && nombre > titulo, t);
    }

    @Test
    void sinSolicitudUsaElAutorizadorDeLaCaja() {
        GastoDto dto = gasto();
        dto.setAutorizadoPor(funcionario("PEDRO GOMEZ"));
        String t = imprimir(dto);
        assertTrue(t.contains("Autorizado por: PEDRO GOMEZ"));
        // El autorizador no firma: solo queda la firma del responsable.
        assertFalse(t.contains("AUTORIZACION"));
        assertTrue(t.contains("FIRMA RESPONSABLE"));
    }

    @Test
    void gastoSinAutorizacionNoImprimeLaLinea() {
        String t = imprimir(gasto());
        assertFalse(t.contains("Autorizado por"));
        assertTrue(t.contains("FIRMA RESPONSABLE"));
    }
}
