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
