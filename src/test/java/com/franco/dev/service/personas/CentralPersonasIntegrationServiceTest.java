package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CentralPersonasIntegrationServiceTest {

    private JdbcTemplate jdbc;
    private CentralPersonasIntegrationService s;
    private MockRestServiceServer central;

    @BeforeEach
    void setUp() {
        Environment env = mock(Environment.class);
        when(env.getProperty("ipServidorCentral")).thenReturn("central:8081");
        jdbc = mock(JdbcTemplate.class);
        s = new CentralPersonasIntegrationService(env, jdbc);
        s.esperaReplicaMs = 200;
        s.pasoReplicaMs = 10;
        central = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(s, "restTemplate")).build();
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void elAltaReenviaElTokenDelRequestYLeeLaRespuestaDelCentral() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Token abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        // forma de la respuesta del central (respuesta(Persona)), con un campo que el filial no conoce
        central.expect(requestTo("http://central:8081/api/personas"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Token abc"))
                .andRespond(withSuccess("{\"id\":321,\"nombre\":\"EMPRESA SA\",\"documento\":\"80018977\",\"usuario\":null,"
                        + "\"creadoEn\":\"2026-10-25T10:15:30\",\"campoNuevoDelCentral\":1}", MediaType.APPLICATION_JSON));
        Persona p = new Persona();
        p.setDocumento("80018977-1");
        Persona r = s.syncPersona(p);
        central.verify();
        assertEquals(Long.valueOf(321L), r.getId());
        assertEquals("80018977", r.getDocumento());
        assertEquals(2026, r.getCreadoEn().getYear());
    }

    @Test
    void sinRequestNoMandaTokenYUn401Lanza() {
        central.expect(requestTo("http://central:8081/api/personas"))
                .andExpect(r -> assertFalse(r.getHeaders().containsKey("Authorization")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));
        assertThrows(IllegalStateException.class, () -> s.syncPersona(new Persona()));
        central.verify();
    }

    @Test
    void suscripcion() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("personas.persona"))).thenReturn(true, false);
        assertTrue(s.llegaPorReplica("personas.persona"));
        assertFalse(s.llegaPorReplica("personas.persona"));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("personas.cliente"))).thenThrow(new RuntimeException("x"));
        assertTrue(s.llegaPorReplica("personas.cliente"));
    }

    @Test
    void esperarReplica() {
        AtomicInteger n = new AtomicInteger();
        assertEquals(Optional.of("fila"), s.esperarReplica(() -> n.incrementAndGet() < 3 ? Optional.empty() : Optional.of("fila")));
        long t = System.currentTimeMillis();
        assertFalse(s.esperarReplica(Optional::empty).isPresent());
        assertTrue(System.currentTimeMillis() - t >= 200);
    }
}
