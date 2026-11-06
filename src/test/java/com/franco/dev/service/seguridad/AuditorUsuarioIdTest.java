package com.franco.dev.service.seguridad;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditorUsuarioIdTest {

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void capturarLog() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuditorUsuarioId.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void soltarLog() {
        logger.detachAppender(appender);
    }

    private List<String> hallazgos() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains(AuditorUsuarioId.MARCADOR))
                .collect(Collectors.toList());
    }

    private AuditorUsuarioId auditorCon(UsuarioSesionService sesion) {
        AuditorUsuarioId auditor = new AuditorUsuarioId();
        auditor.setUsuarioSesionService(sesion);
        MockEnvironment env = new MockEnvironment();
        env.setProperty("sucursalId", "24");
        auditor.setEnv(env);
        return auditor;
    }

    private static UsuarioSesionService sesionDe(Long id) {
        UsuarioSesionService s = mock(UsuarioSesionService.class);
        when(s.idDelUsuarioAutenticado()).thenReturn(Optional.ofNullable(id));
        return s;
    }

    @Test
    @DisplayName("registra cuando el input no coincide con la sesion")
    void registraElMismatch() {
        auditorCon(sesionDe(410L)).verificar("VentaGraphQL.saveVenta", 999L);

        List<String> h = hallazgos();
        assertEquals(1, h.size());
        assertTrue(h.get(0).contains("operacion=VentaGraphQL.saveVenta"), h.get(0));
        assertTrue(h.get(0).contains("usuarioInput=999"), h.get(0));
        assertTrue(h.get(0).contains("usuarioSesion=410"), h.get(0));
        assertTrue(h.get(0).contains("sucursal=24"), h.get(0));
    }

    @Test
    @DisplayName("no registra cuando coinciden")
    void noRegistraCuandoCoinciden() {
        auditorCon(sesionDe(410L)).verificar("VentaGraphQL.saveVenta", 410L);
        assertTrue(hallazgos().isEmpty());
    }

    @Test
    @DisplayName("no registra sin sesion: es un proceso de sistema")
    void noRegistraSinSesion() {
        auditorCon(sesionDe(null)).verificar("VentaGraphQL.saveVenta", 999L);
        assertTrue(hallazgos().isEmpty(), "los schedulers y la replicacion postean sin sesion");
    }

    @Test
    @DisplayName("no registra si el input no trae usuarioId")
    void noRegistraSinUsuarioEnElInput() {
        auditorCon(sesionDe(410L)).verificar("VentaGraphQL.saveVenta", null);
        assertTrue(hallazgos().isEmpty());
    }

    @Test
    @DisplayName("la property lo apaga sin redesplegar")
    void laPropertyLoApaga() {
        AuditorUsuarioId auditor = new AuditorUsuarioId();
        auditor.setUsuarioSesionService(sesionDe(410L));
        MockEnvironment env = new MockEnvironment();
        env.setProperty(AuditorUsuarioId.PROPERTY_HABILITADO, "false");
        auditor.setEnv(env);

        auditor.verificar("VentaGraphQL.saveVenta", 999L);

        assertTrue(hallazgos().isEmpty());
    }

    // ---- firmante (issue #142, fase de enforcement) ----

    private static UsuarioSesionService sesionActivaDe(Long id, boolean activo) {
        UsuarioSesionService s = mock(UsuarioSesionService.class);
        when(s.idDelUsuarioAutenticado()).thenReturn(Optional.ofNullable(id));
        when(s.idDelUsuarioActivoAutenticado()).thenReturn(activo ? Optional.ofNullable(id) : Optional.empty());
        return s;
    }

    private AuditorUsuarioId firmanteCon(UsuarioSesionService sesion, String sesionManda) {
        AuditorUsuarioId auditor = auditorCon(sesion);
        MockEnvironment env = new MockEnvironment();
        env.setProperty("sucursalId", "24");
        if (sesionManda != null) env.setProperty(AuditorUsuarioId.PROPERTY_SESION_MANDA, sesionManda);
        auditor.setEnv(env);
        return auditor;
    }

    @Test
    @DisplayName("prendido: la sesion firma y el reemplazo queda en el log con la caja")
    void laSesionFirma() {
        Long firmante = firmanteCon(sesionActivaDe(410L, true), "true")
                .firmante("VentaGraphQL.saveVenta", 999L, 77L);

        assertEquals(410L, firmante);
        List<String> h = hallazgos();
        assertEquals(1, h.size());
        assertTrue(h.get(0).contains("accion=reemplazado"), h.get(0));
        assertTrue(h.get(0).contains("usuarioInput=999"), h.get(0));
        assertTrue(h.get(0).contains("usuarioSesion=410"), h.get(0));
        assertTrue(h.get(0).contains("cajaId=77"), h.get(0));
    }

    @Test
    @DisplayName("por defecto (sin la property) firma el input y solo registra, como la fase 1")
    void porDefectoFirmaElInput() {
        Long firmante = firmanteCon(sesionActivaDe(410L, true), null)
                .firmante("VentaGraphQL.saveVenta", 999L, 77L);

        assertEquals(999L, firmante);
        List<String> h = hallazgos();
        assertEquals(1, h.size());
        assertFalse(h.get(0).contains("accion=reemplazado"), h.get(0));
    }

    @Test
    @DisplayName("sin sesion firma el input")
    void sinSesionFirmaElInput() {
        assertEquals(999L, firmanteCon(sesionActivaDe(null, false), "true")
                .firmante("VentaGraphQL.saveVenta", 999L, null));
        assertTrue(hallazgos().isEmpty());
    }

    @Test
    @DisplayName("con la sesion de un usuario inactivo firma el input y lo registra")
    void sesionInactivaFirmaElInput() {
        assertEquals(641L, firmanteCon(sesionActivaDe(520L, false), "true")
                .firmante("VentaGraphQL.saveVenta", 641L, 5L));
        List<String> h = hallazgos();
        assertEquals(1, h.size());
        assertTrue(h.get(0).contains("accion=sesion-inactiva"), h.get(0));
    }

    @Test
    @DisplayName("un input nulo queda nulo: no se completan los que hoy viajan vacios")
    void inputNuloQuedaNulo() {
        assertNull(firmanteCon(sesionActivaDe(410L, true), "true")
                .firmante("ConteoGraphQL.saveConteo", null, 5L));
    }

    @Test
    @DisplayName("el reemplazo se registra aunque la auditoria de la fase 1 este apagada")
    void reemplazaYRegistraConLaAuditoriaApagada() {
        AuditorUsuarioId auditor = auditorCon(sesionActivaDe(410L, true));
        MockEnvironment env = new MockEnvironment();
        env.setProperty(AuditorUsuarioId.PROPERTY_HABILITADO, "false");
        env.setProperty(AuditorUsuarioId.PROPERTY_SESION_MANDA, "true");
        auditor.setEnv(env);

        assertEquals(410L, auditor.firmante("VentaGraphQL.saveVenta", 999L, null));
        assertEquals(1, hallazgos().size());
    }

    @Test
    @DisplayName("secundario apagado: firma el input y no duplica la linea del principal")
    void secundarioApagadoNoRegistra() {
        assertEquals(999L, firmanteCon(sesionActivaDe(410L, true), null)
                .firmanteSecundario("VentaGraphQL.saveVenta", 999L, 77L));
        assertTrue(hallazgos().isEmpty());
    }

    @Test
    @DisplayName("secundario prendido: se firma por su cuenta aunque el principal ya coincida")
    void secundarioPrendidoReemplaza() {
        assertEquals(410L, firmanteCon(sesionActivaDe(410L, true), "true")
                .firmanteSecundario("VentaGraphQL.saveVenta", 999L, 77L));
        assertEquals(1, hallazgos().size());
        assertTrue(hallazgos().get(0).contains("accion=reemplazado"));
    }

    @Test
    @DisplayName("si la sesion revienta, firma el input sin lanzar")
    void firmanteNuncaLanza() {
        UsuarioSesionService roto = mock(UsuarioSesionService.class);
        when(roto.idDelUsuarioAutenticado()).thenThrow(new IllegalStateException("base caida"));

        assertEquals(999L, assertDoesNotThrow(() -> firmanteCon(roto, "true")
                .firmante("VentaGraphQL.saveVenta", 999L, null)));
    }

    @Test
    @DisplayName("si el resolutor de sesion revienta, no se escapa la excepcion")
    void nuncaLanza() {
        UsuarioSesionService roto = mock(UsuarioSesionService.class);
        when(roto.idDelUsuarioAutenticado()).thenThrow(new IllegalStateException("base caida"));

        AuditorUsuarioId auditor = auditorCon(roto);

        assertDoesNotThrow(() -> auditor.verificar("VentaGraphQL.saveVenta", 999L),
                "un instrumento de observabilidad no puede tumbar una venta");
        assertTrue(hallazgos().isEmpty(), "una falla del auditor no es un hallazgo de auditoria");
    }
}
