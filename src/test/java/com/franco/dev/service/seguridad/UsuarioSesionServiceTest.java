package com.franco.dev.service.seguridad;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsuarioSesionServiceTest {

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticarComo(String nickname) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new User(nickname, "x", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))),
                        "x",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static Usuario usuarioConId(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        return u;
    }

    @Test
    @DisplayName("resuelve el id del usuario del token")
    void resuelveElIdDelToken() {
        UsuarioService usuarios = mock(UsuarioService.class);
        when(usuarios.findByNickname(anyString())).thenReturn(Optional.of(usuarioConId(410L)));

        UsuarioSesionService servicio = new UsuarioSesionService();
        servicio.setUsuarioService(usuarios);
        autenticarComo("MAURO");

        assertEquals(Optional.of(410L), servicio.idDelUsuarioAutenticado());
    }

    @Test
    @DisplayName("sin contexto de seguridad devuelve vacio")
    void sinContextoDevuelveVacio() {
        UsuarioSesionService servicio = new UsuarioSesionService();
        servicio.setUsuarioService(mock(UsuarioService.class));

        assertFalse(servicio.idDelUsuarioAutenticado().isPresent());
    }

    @Test
    @DisplayName("un principal anonimo o que no es UserDetails devuelve vacio")
    void principalNoUtilizableDevuelveVacio() {
        UsuarioSesionService servicio = new UsuarioSesionService();
        servicio.setUsuarioService(mock(UsuarioService.class));

        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("clave", "anonimo",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertFalse(servicio.idDelUsuarioAutenticado().isPresent(),
                "un principal que no es UserDetails no puede resolverse a un id");
    }

    @Test
    @DisplayName("el cache evita el segundo viaje a la base")
    void elCacheEvitaElSegundoViaje() {
        AtomicInteger viajes = new AtomicInteger();
        UsuarioService usuarios = mock(UsuarioService.class);
        when(usuarios.findByNickname(anyString())).thenAnswer(inv -> {
            viajes.incrementAndGet();
            return Optional.of(usuarioConId(410L));
        });

        UsuarioSesionService servicio = new UsuarioSesionService();
        servicio.setUsuarioService(usuarios);
        autenticarComo("MAURO");

        servicio.idDelUsuarioAutenticado();
        servicio.idDelUsuarioAutenticado();
        servicio.idDelUsuarioAutenticado();

        assertEquals(1, viajes.get(), "el segundo y el tercero tienen que salir del cache");
    }

    @Test
    @DisplayName("pasado el TTL vuelve a consultar: un rename no queda cacheado para siempre")
    void pasadoElTtlVuelveAConsultar() {
        AtomicInteger viajes = new AtomicInteger();
        UsuarioService usuarios = mock(UsuarioService.class);
        when(usuarios.findByNickname(anyString())).thenAnswer(inv -> {
            viajes.incrementAndGet();
            return Optional.of(usuarioConId(410L));
        });

        AtomicLong reloj = new AtomicLong(0L);
        UsuarioSesionService servicio = new UsuarioSesionService(reloj::get);
        servicio.setUsuarioService(usuarios);
        autenticarComo("MAURO");

        servicio.idDelUsuarioAutenticado();
        reloj.set(61_000L);
        servicio.idDelUsuarioAutenticado();

        assertEquals(2, viajes.get(), "pasado el TTL la entrada vencida no se reusa");
    }

    @Test
    @DisplayName("un nickname que no resuelve devuelve vacio y no golpea la base de nuevo")
    void nicknameQueNoResuelve() {
        AtomicInteger viajes = new AtomicInteger();
        UsuarioService usuarios = mock(UsuarioService.class);
        when(usuarios.findByNickname(anyString())).thenAnswer(inv -> {
            viajes.incrementAndGet();
            return Optional.empty();
        });

        UsuarioSesionService servicio = new UsuarioSesionService();
        servicio.setUsuarioService(usuarios);
        autenticarComo("FANTASMA");

        assertFalse(servicio.idDelUsuarioAutenticado().isPresent());
        assertFalse(servicio.idDelUsuarioAutenticado().isPresent());
        assertEquals(1, viajes.get(), "el resultado negativo tambien se cachea");
    }

    /**
     * Test estructural, no de comportamiento. Verificar de verdad que la transaccion de la venta
     * sobrevive necesita un contexto Spring con una base, y este repo no tiene ningun
     * {@code @SpringBootTest} (grep en src/test = 0). Lo que si se puede garantizar en la bateria
     * es que el mecanismo siga declarado: si alguien saca la anotacion, esto corta el CI.
     */
    @Test
    @DisplayName("la resolucion corre fuera de la transaccion que la llama")
    void correFueraDeLaTransaccion() throws Exception {
        Method m = UsuarioSesionService.class.getMethod("idDelUsuarioAutenticado");
        Transactional t = m.getAnnotation(Transactional.class);

        assertNotNull(t, "sin @Transactional, un fallo del SELECT deja la venta rollback-only");
        assertEquals(Propagation.NOT_SUPPORTED, t.propagation(),
                "tiene que suspender la transaccion de la venta, como ConfiguracionFacturacionLector");
        assertTrue(t.readOnly(), "es una lectura");
    }
}
