package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.ConteoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * saveConteo de la filial es el que termina escribiendo el conteo de una caja, venga del PDV o
 * del admin a traves del central. Una caja inexistente tiene que dar un error que el usuario
 * entienda (antes volvia null y el cliente mostraba un error de tipo no-nulo), y un segundo
 * conteo de apertura no puede persistir otro conteo con sus movimientos.
 */
class ConteoGraphQLSaveConteoTest {

    private static final Long CAJA = 50L;
    private static final Long SUCURSAL = 3L;

    private ConteoService service;
    private PdvCajaService pdvCajaService;
    private ConteoGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(ConteoService.class);
        pdvCajaService = mock(PdvCajaService.class);
        SucursalService sucursalService = mock(SucursalService.class);
        MonedaService monedaService = mock(MonedaService.class);

        resolver = new ConteoGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "pdvCajaService", pdvCajaService);
        ReflectionTestUtils.setField(resolver, "sucursalService", sucursalService);
        ReflectionTestUtils.setField(resolver, "monedaService", monedaService);
        ReflectionTestUtils.setField(resolver, "usuarioService", mock(UsuarioService.class));
        ReflectionTestUtils.setField(resolver, "auditorUsuarioId", mock(AuditorUsuarioId.class));
        ReflectionTestUtils.setField(resolver, "conteoMonedaGraphQL", mock(ConteoMonedaGraphQL.class));

        Sucursal sucursal = new Sucursal();
        sucursal.setId(SUCURSAL);
        when(sucursalService.sucursalActual()).thenReturn(sucursal);
        when(monedaService.findAll2()).thenReturn(Collections.emptyList());
        when(service.saveAndSend(any(Conteo.class), anyBoolean())).thenAnswer(inv -> {
            Conteo c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });
    }

    private ConteoInput input() {
        ConteoInput input = new ConteoInput();
        input.setTotalGs(100000.0);
        input.setTotalRs(0.0);
        input.setTotalDs(0.0);
        return input;
    }

    private PdvCaja caja() {
        PdvCaja caja = new PdvCaja();
        caja.setId(CAJA);
        return caja;
    }

    @Test
    void cajaInexistenteDaErrorConMensaje() {
        when(pdvCajaService.findByIdForUpdate(CAJA)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.saveConteo(input(), null, CAJA, true, false));

        assertTrue(e.getMessage().contains("No se encontro la caja id=" + CAJA));
        verify(service, never()).saveAndSend(any(Conteo.class), anyBoolean());
    }

    @Test
    void aperturaRepetidaDevuelveElConteoExistenteSinPersistir() {
        Conteo existente = new Conteo();
        existente.setId(10L);
        PdvCaja caja = caja();
        caja.setConteoApertura(existente);
        when(pdvCajaService.findByIdForUpdate(CAJA)).thenReturn(Optional.of(caja));

        Conteo res = resolver.saveConteo(input(), null, CAJA, true, false);

        assertSame(existente, res);
        verify(service, never()).saveAndSend(any(Conteo.class), anyBoolean());
        verify(pdvCajaService, never()).saveAndSend(any(PdvCaja.class), anyBoolean());
    }

    @Test
    void aperturaSobreCajaSinConteoPersisteYEnlaza() {
        PdvCaja caja = caja();
        when(pdvCajaService.findByIdForUpdate(CAJA)).thenReturn(Optional.of(caja));

        Conteo res = resolver.saveConteo(input(), null, CAJA, true, false);

        assertNotNull(res);
        assertEquals(Long.valueOf(99L), res.getId());
        assertSame(res, caja.getConteoApertura());
        verify(pdvCajaService).saveAndSend(caja, false);
    }

    @Test
    void laCajaSeLeeConLockPesimista() {
        when(pdvCajaService.findByIdForUpdate(CAJA)).thenReturn(Optional.of(caja()));

        resolver.saveConteo(input(), null, CAJA, false, false);

        verify(pdvCajaService).findByIdForUpdate(CAJA);
        verify(pdvCajaService, never()).findById(anyLong());
    }
}
