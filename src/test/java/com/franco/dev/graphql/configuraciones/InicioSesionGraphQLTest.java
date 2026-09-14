package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.configuraciones.input.InicioSesionInput;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class InicioSesionGraphQLTest {

    private InicioSesionService service;

    private SucursalService sucursalService;

    private InicioSesionGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(InicioSesionService.class);
        sucursalService = mock(SucursalService.class);
        resolver = new InicioSesionGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "sucursalService", sucursalService);
        ReflectionTestUtils.setField(resolver, "usuarioService", mock(UsuarioService.class));
        when(service.saveAndSend(any(InicioSesion.class), anyBoolean())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Sucursal sucursal(long id) {
        Sucursal s = new Sucursal();
        s.setId(id);
        return s;
    }

    @Test
    void altaConSucursalCero_seGuardaConLaSucursalDelFilial() {
        when(sucursalService.sucursalActual()).thenReturn(sucursal(24L));
        InicioSesionInput input = new InicioSesionInput();
        input.setSucursalId(0L);

        InicioSesion guardada = resolver.saveInicioSesion(input);

        assertEquals(Long.valueOf(24L), guardada.getSucursal().getId());
    }

    @Test
    void altaSinSucursal_seGuardaConLaSucursalDelFilial() {
        when(sucursalService.sucursalActual()).thenReturn(sucursal(24L));

        InicioSesion guardada = resolver.saveInicioSesion(new InicioSesionInput());

        assertEquals(Long.valueOf(24L), guardada.getSucursal().getId());
    }

    @Test
    void actualizacionDeSesionPropia_conservaSucursalYNoPisaCamposQueNoLlegan() {
        when(sucursalService.sucursalActual()).thenReturn(sucursal(24L));
        InicioSesion existente = new InicioSesion();
        existente.setId(10L);
        existente.setSucursal(sucursal(24L));
        existente.setToken("TOKEN");
        existente.setIdDispositivo("DISPOSITIVO");
        when(service.findByIdAndSucursalId(10L, 24L)).thenReturn(Optional.of(existente));
        InicioSesionInput input = new InicioSesionInput();
        input.setId(10L);
        input.setSucursalId(0L);
        input.setHoraFin("2026-09-14 18:30");

        InicioSesion guardada = resolver.saveInicioSesion(input);

        assertEquals(Long.valueOf(24L), guardada.getSucursal().getId());
        assertEquals("TOKEN", guardada.getToken());
        assertEquals("DISPOSITIVO", guardada.getIdDispositivo());
        assertEquals(LocalDateTime.of(2026, 9, 14, 18, 30), guardada.getHoraFin());
    }

    @Test
    void actualizacionDeSesionDeOtraSucursal_seRechazaSinGuardar() {
        when(sucursalService.sucursalActual()).thenReturn(sucursal(24L));
        when(service.findByIdAndSucursalId(99L, 24L)).thenReturn(Optional.empty());
        InicioSesionInput input = new InicioSesionInput();
        input.setId(99L);
        input.setSucursalId(0L);

        assertThrows(GraphQLException.class, () -> resolver.saveInicioSesion(input));
        verify(service, never()).saveAndSend(any(InicioSesion.class), anyBoolean());
    }

    @Test
    void servidorSinSucursal_seRechazaSinGuardar() {
        when(sucursalService.sucursalActual()).thenReturn(null);

        assertThrows(GraphQLException.class, () -> resolver.saveInicioSesion(new InicioSesionInput()));
        verify(service, never()).saveAndSend(any(InicioSesion.class), anyBoolean());
    }

    @Test
    void servidorConLaSucursalDeCentral_seRechazaSinGuardar() {
        when(sucursalService.sucursalActual()).thenReturn(sucursal(0L));

        assertThrows(GraphQLException.class, () -> resolver.saveInicioSesion(new InicioSesionInput()));
        verify(service, never()).saveAndSend(any(InicioSesion.class), anyBoolean());
    }

    @Test
    void servidorSinPropertySucursalId_seRechazaSinGuardar() {
        when(sucursalService.sucursalActual()).thenThrow(new NumberFormatException("null"));

        assertThrows(GraphQLException.class, () -> resolver.saveInicioSesion(new InicioSesionInput()));
        verify(service, never()).saveAndSend(any(InicioSesion.class), anyBoolean());
    }
}
