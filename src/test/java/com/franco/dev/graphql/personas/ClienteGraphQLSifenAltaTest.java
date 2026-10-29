package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.sifen.service.SifenService;
import com.franco.dev.service.sifen.dto.response.ConsultaRucResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** El alta del cliente desde SIFEN no reusa el id de la persona como id del cliente (el central lo tomaria como edicion). */
class ClienteGraphQLSifenAltaTest {

    @Test
    void elClienteNuevoVaSinId() {
        for (String metodo : new String[]{"crearClienteDesdeSifen", "crearClienteDesdeSifenConResponse"}) {
            ClienteGraphQL g = new ObjenesisStd().newInstance(ClienteGraphQL.class);
            ClienteService clientes = mock(ClienteService.class);
            PersonaService personas = mock(PersonaService.class);
            SifenService sifen = mock(SifenService.class);
            ReflectionTestUtils.setField(g, "service", clientes);
            ReflectionTestUtils.setField(g, "personaService", personas);
            ReflectionTestUtils.setField(g, "sifenService", sifen);
            ConsultaRucResponse r = new ConsultaRucResponse();
            r.setRuc("80018977");
            r.setRazonSocial("EMPRESA SA");
            when(sifen.isSifenEnabled()).thenReturn(true);
            when(sifen.consultaRuc(anyString())).thenReturn(r);
            Persona existente = new Persona();
            existente.setId(50L);
            existente.setDocumento("80018977");
            when(personas.findByDocumento("80018977")).thenReturn(existente);
            when(personas.save(any())).thenThrow(new IllegalStateException("edicion"));
            when(clientes.save(any())).thenAnswer(i -> i.getArgument(0));

            ReflectionTestUtils.invokeMethod(g, metodo, "80018977");

            ArgumentCaptor<Cliente> cap = ArgumentCaptor.forClass(Cliente.class);
            verify(clientes).save(cap.capture());
            assertNull(cap.getValue().getId(), metodo);
            assertEquals(Long.valueOf(50L), cap.getValue().getPersona().getId(), metodo);
        }
    }

    @Test
    void altaConPersonaNuevaEnMinusculasNoIntentaEditarla() {
        ClienteGraphQL g = new ObjenesisStd().newInstance(ClienteGraphQL.class);
        ClienteService clientes = mock(ClienteService.class);
        PersonaService personas = mock(PersonaService.class);
        ReflectionTestUtils.setField(g, "service", clientes);
        ReflectionTestUtils.setField(g, "personaService", personas);
        Persona delCentral = new Persona();
        delCentral.setId(321L);
        delCentral.setNombre("EMPRESA SA");
        delCentral.setDireccion("CALLE 1");
        when(personas.save(any())).thenReturn(delCentral);
        when(clientes.save(any())).thenAnswer(i -> i.getArgument(0));
        com.franco.dev.graphql.personas.input.ClienteInput in = new com.franco.dev.graphql.personas.input.ClienteInput();
        in.setDocumento("9990001");
        in.setNombre("empresa sa");
        in.setDireccion("calle 1");

        Cliente c = g.saveCliente(in);

        verify(personas, times(1)).save(any());
        assertEquals(Long.valueOf(321L), c.getPersona().getId());
    }
}
