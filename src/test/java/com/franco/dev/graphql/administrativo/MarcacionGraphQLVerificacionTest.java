package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Un rostro que no coincide se rechaza sin decir cuanto se parecio. */
class MarcacionGraphQLVerificacionTest {

    @Mock private AuditorUsuarioId auditorUsuarioId;
    @Mock private MarcacionService service;
    @Mock private UsuarioService usuarioService;
    @InjectMocks private MarcacionGraphQL graphQL;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        Persona p = new Persona();
        p.setEmbedding("[1.0, 0.0, 0.0]");
        Usuario u = new Usuario();
        u.setId(7L);
        u.setPersona(p);
        when(usuarioService.findById(7L)).thenReturn(Optional.of(u));
    }

    @Test
    void unRostroQueNoCoincideSeRechazaSinElPorcentaje() {
        MarcacionInput input = new MarcacionInput();
        input.setUsuarioId(7L);
        input.setEmbedding(Arrays.asList(0.0, 1.0, 0.0)); // similitud 0

        GraphQLException e = assertThrows(GraphQLException.class, () -> graphQL.saveMarcacion(input));

        assertEquals("Verificación facial fallida: el rostro no coincide", e.getMessage());
        assertFalse(e.getMessage().contains("%"));
        verify(service, never()).save(any());
    }
}
