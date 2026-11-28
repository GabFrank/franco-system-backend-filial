package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.RolSesionService;
import com.franco.dev.service.seguridad.UsuarioSesionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** {@code embeddingFacial} con {@code personas.embedding-facial.restringido}. */
class PersonaResolverGaleriaFacialTest {

    private static final String GALERIA = "{\"master\":[0.1]}";
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private final UsuarioSesionService sesion = mock(UsuarioSesionService.class);
    private final RoleService roles = mock(RoleService.class);
    private final PersonaResolver resolver = new PersonaResolver();
    private final Persona propia = persona(10L);
    private final Persona ajena = persona(20L);

    @BeforeEach
    void armar() {
        RolSesionService seg = new RolSesionService();
        ReflectionTestUtils.setField(seg, "usuarioSesionService", sesion);
        ReflectionTestUtils.setField(seg, "roleService", roles);
        ReflectionTestUtils.setField(resolver, "rolSesionService", seg);
        ReflectionTestUtils.setField(resolver, "usuarioService", usuarios);
        ReflectionTestUtils.setField(resolver, "usuarioSesionService", sesion);
        ReflectionTestUtils.setField(resolver, "embeddingRestringido", true);
        when(usuarios.findByPersonaId(10L)).thenReturn(usuario(1L));
        when(usuarios.findByPersonaId(20L)).thenReturn(usuario(2L));
        iniciarSesion("PEPE", 1L);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void laPropiaSinConsultarRoles() {
        assertEquals(GALERIA, resolver.embeddingFacial(propia));
        verify(roles, never()).findByUsuarioId(anyLong());
    }

    @Test
    void ajenaSinRolEsNull() {
        when(roles.findByUsuarioId(1L)).thenReturn(Collections.singletonList(rol("CAJERO")));
        assertNull(resolver.embeddingFacial(ajena));
    }

    @Test
    void ajenaConRrhhGestionarOAdmin() {
        when(roles.findByUsuarioId(1L)).thenReturn(Arrays.asList(rol("CAJERO"), rol("RRHH GESTIONAR")));
        assertEquals(GALERIA, resolver.embeddingFacial(ajena));
        when(roles.findByUsuarioId(1L)).thenReturn(Collections.singletonList(rol("ADMIN")));
        assertEquals(GALERIA, resolver.embeddingFacial(ajena));
    }

    @Test
    void nicknameAdmin() {
        iniciarSesion("ADMIN", 3L);
        assertEquals(GALERIA, resolver.embeddingFacial(ajena));
    }

    @Test
    void personaSinUsuarioSoloConRol() {
        when(usuarios.findByPersonaId(20L)).thenReturn(null);
        when(roles.findByUsuarioId(1L)).thenReturn(Collections.emptyList());
        assertNull(resolver.embeddingFacial(ajena));
    }

    @Test
    void sinSesionOUsuarioInexistenteEsNull() {
        SecurityContextHolder.clearContext();
        assertNull(resolver.embeddingFacial(propia));
        iniciarSesion("NADIE", null);
        assertNull(resolver.embeddingFacial(propia));
    }

    @Test
    void inactivoEsNull() {
        when(sesion.idDelUsuarioActivoAutenticado()).thenReturn(Optional.empty());
        assertNull(resolver.embeddingFacial(propia));
    }

    @Test
    void apagadoComoAntes() {
        ReflectionTestUtils.setField(resolver, "embeddingRestringido", false);
        SecurityContextHolder.clearContext();
        assertEquals(GALERIA, resolver.embeddingFacial(ajena));
    }

    private void iniciarSesion(String nick, Long usuarioId) {
        when(sesion.idDelUsuarioAutenticado()).thenReturn(Optional.ofNullable(usuarioId));
        when(sesion.idDelUsuarioActivoAutenticado()).thenReturn(Optional.ofNullable(usuarioId));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(nick, null, "USER"));
    }

    private static Usuario usuario(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        return u;
    }

    private static Persona persona(Long id) {
        Persona p = new Persona();
        p.setId(id);
        p.setEmbedding(GALERIA);
        return p;
    }

    private static Role rol(String nombre) {
        Role r = new Role();
        r.setNombre(nombre);
        return r;
    }
}
