package com.franco.dev.service.seguridad;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.service.personas.RoleService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RolSesionServiceTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    static void autenticarComo(String nickname) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new User(nickname, "x", Collections.singletonList(new SimpleGrantedAuthority("null"))), "x",
                Collections.singletonList(new SimpleGrantedAuthority("null"))));
    }

    /** Servicio con el usuario 7 y los roles dados en la base; usuarioId vacio = el nickname no existe. */
    static RolSesionService servicio(Optional<Long> usuarioId, String... roles) {
        RolSesionService s = new RolSesionService();
        UsuarioSesionService sesion = mock(UsuarioSesionService.class);
        when(sesion.idDelUsuarioAutenticado()).thenReturn(usuarioId);
        RoleService roleService = mock(RoleService.class);
        List<Role> lista = Arrays.stream(roles).map(n -> {
            Role r = new Role();
            r.setNombre(n);
            return r;
        }).collect(Collectors.toList());
        when(roleService.findByUsuarioId(7L)).thenReturn(lista);
        ReflectionTestUtils.setField(s, "usuarioSesionService", sesion);
        ReflectionTestUtils.setField(s, "roleService", roleService);
        return s;
    }

    static String rechazo(Runnable r) {
        GraphQLException e = assertThrows(GraphQLException.class, r::run);
        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        return e.getMessage();
    }

    @Test
    void sinSesionSeRechaza() {
        RolSesionService s = servicio(Optional.of(7L), "ADMIN");
        assertTrue(rechazo(s::exigirAdmin).contains("iniciar sesión"));
    }

    @Test
    void elNicknameAdminPasaSinMirarLaBase() {
        autenticarComo("admin");
        RolSesionService s = servicio(Optional.empty());
        s.exigirAdmin();
        s.exigirRol("CONFIGURACION");
    }

    @Test
    void elRolAdminPasaEnTodo() {
        autenticarComo("MAURO");
        RolSesionService s = servicio(Optional.of(7L), "ADMIN");
        s.exigirAdmin();
        s.exigirRol("ANALISIS DE CAJA");
    }

    @Test
    void unRolPedidoPasaAunqueTengaEspaciosYMinusculas() {
        autenticarComo("MAURO");
        servicio(Optional.of(7L), " configuracion ").exigirRol("ANALISIS DE CAJA", "CONFIGURACION");
    }

    @Test
    void sinElRolSeRechazaYNoAlcanzaParaAdmin() {
        autenticarComo("MAURO");
        RolSesionService s = servicio(Optional.of(7L), "VER TRANSFERENCIA");
        assertTrue(rechazo(() -> s.exigirRol("CONFIGURACION")).contains("CONFIGURACION"));
        assertTrue(rechazo(s::exigirAdmin).contains("ADMIN"));
        RolSesionService conConfig = servicio(Optional.of(7L), "CONFIGURACION");
        rechazo(conConfig::exigirAdmin);
    }

    @Test
    void unUsuarioQueNoExisteEnLaSucursalSeRechaza() {
        autenticarComo("NUEVO");
        assertTrue(rechazo(() -> servicio(Optional.empty()).exigirRol("CONFIGURACION")).contains("no existe"));
    }

    private static RoleService roles(RolSesionService s) {
        return (RoleService) ReflectionTestUtils.getField(s, "roleService");
    }

    @Test
    void elDuenioPasaSinConsultarRoles() {
        autenticarComo("CAJERO");
        RolSesionService s = servicio(Optional.of(7L));
        assertTrue(s.esDuenioOTieneRol(7L, RolSesionService.ANALISIS_DE_CAJA));
        verify(roles(s), never()).findByUsuarioId(anyLong());
    }

    @Test
    void elQueNoEsDuenioNecesitaElRol() {
        autenticarComo("CAJERO");
        assertFalse(servicio(Optional.of(7L), "VENTA TOUCH").esDuenioOTieneRol(5L, RolSesionService.ANALISIS_DE_CAJA));
        assertTrue(servicio(Optional.of(7L), "ANALISIS DE CAJA").esDuenioOTieneRol(5L, RolSesionService.ANALISIS_DE_CAJA));
        assertTrue(servicio(Optional.of(7L), "ADMIN").esDuenioOTieneRol(5L, RolSesionService.ANALISIS_DE_CAJA));
    }

    @Test
    void cajaSinDuenioSoloConElRol() {
        autenticarComo("CAJERO");
        assertFalse(servicio(Optional.of(7L)).esDuenioOTieneRol(null, RolSesionService.ANALISIS_DE_CAJA));
        assertTrue(servicio(Optional.of(7L), "ANALISIS DE CAJA").esDuenioOTieneRol(null, RolSesionService.ANALISIS_DE_CAJA));
    }

    @Test
    void sinSesionOUsuarioInexistenteNoPasa() {
        assertFalse(servicio(Optional.of(7L), "ADMIN").esDuenioOTieneRol(7L));
        autenticarComo("NUEVO");
        assertFalse(servicio(Optional.empty()).esDuenioOTieneRol(7L, RolSesionService.ANALISIS_DE_CAJA));
    }

    @Test
    void elNicknameAdminPasaComoDuenio() {
        autenticarComo("admin");
        assertTrue(servicio(Optional.empty()).esDuenioOTieneRol(5L, RolSesionService.ANALISIS_DE_CAJA));
    }

    @Test
    void exigirDuenioORolLanzaNoAutorizado() {
        autenticarComo("CAJERO");
        RolSesionService s = servicio(Optional.of(7L));
        assertTrue(rechazo(() -> s.exigirDuenioORol(5L, RolSesionService.ANALISIS_DE_CAJA)).contains("ANALISIS DE CAJA"));
        s.exigirDuenioORol(7L, RolSesionService.ANALISIS_DE_CAJA);
    }
}
