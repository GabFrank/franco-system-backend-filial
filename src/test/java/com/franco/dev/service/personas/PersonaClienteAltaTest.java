package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.repository.personas.PersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Solo altas: con la tabla suscripta no se escribe local (trabaria la replica); sin suscripcion, si. */
class PersonaClienteAltaTest {

    private PersonaRepository personas;
    private ClienteRepository clientes;
    private CentralPersonasIntegrationService central;
    private PersonaService personaService;
    private ClienteService clienteService;

    @BeforeEach
    void setUp() {
        personas = mock(PersonaRepository.class);
        clientes = mock(ClienteRepository.class);
        central = mock(CentralPersonasIntegrationService.class);
        when(central.esperarReplica(any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
        personaService = new PersonaService(personas, central);
        clienteService = new ClienteService(clientes, central);
        when(personas.save(any())).thenAnswer(i -> i.getArgument(0));
        when(clientes.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Persona delCentral() {
        Persona p = new Persona();
        p.setId(321L);
        p.setNombre("EMPRESA SA");
        p.setDocumento("80018977");
        return p;
    }

    @Test
    void unaEdicionNoVaAlCentralNiSeEscribe() {
        Persona p = new Persona();
        p.setId(5L);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> personaService.save(p));
        assertEquals(PersonaService.EDICION_EN_CENTRAL, e.getMessage());
        Cliente c = new Cliente();
        c.setId(5L);
        assertThrows(IllegalStateException.class, () -> clienteService.save(c));
        verifyNoInteractions(central);
        verify(personas, never()).save(any());
        verify(clientes, never()).save(any());
    }

    @Test
    void suscriptaYLaFilaLlegaDevuelveLaLocalSinEscribir() {
        when(central.syncPersona(any())).thenReturn(delCentral());
        when(central.llegaPorReplica("personas.persona")).thenReturn(true);
        Persona local = delCentral();
        when(personas.findById(321L)).thenReturn(Optional.of(local));
        assertSame(local, personaService.save(new Persona()));
        verify(personas, never()).save(any());
    }

    @Test
    void suscriptaYLaFilaNoLlegaLanzaSinEscribir() {
        Cliente delCentral = new Cliente();
        delCentral.setId(77L);
        when(central.syncCliente(any())).thenReturn(delCentral);
        when(central.llegaPorReplica("personas.cliente")).thenReturn(true);
        when(clientes.findById(77L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> clienteService.save(new Cliente()));
        verify(clientes, never()).save(any());
    }

    @Test
    void sinSuscripcionGuardaLocalConElIdDelCentral() {
        when(central.syncPersona(any())).thenReturn(delCentral());
        when(central.llegaPorReplica("personas.persona")).thenReturn(false);
        Persona p = new Persona();
        p.setNombre("empresa sa");
        Persona r = personaService.save(p);
        assertEquals(Long.valueOf(321L), r.getId());
        assertNotNull(r.getCreadoEn());
        verify(personas).save(p);
    }

    @Test
    void sinRespuestaDelCentralLanza() {
        when(central.syncCliente(any())).thenReturn(new Cliente());
        assertThrows(IllegalStateException.class, () -> clienteService.save(new Cliente()));
        verify(clientes, never()).save(any());
    }
}
