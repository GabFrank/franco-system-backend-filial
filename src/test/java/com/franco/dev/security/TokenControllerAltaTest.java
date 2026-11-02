package com.franco.dev.security;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.security.jwt.JwtGenerator;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** El filial no da altas de usuario: llegan del central. */
class TokenControllerAltaTest {

    @Test
    void elAltaPorLoginNewEstaCerradaYNoGuardaNada() {
        UsuarioService usuarios = mock(UsuarioService.class);
        TokenController controller = new TokenController(usuarios, mock(JwtGenerator.class));
        Usuario u = new Usuario();
        u.setId(410L);
        u.setNickname("X");
        u.setPassword("x");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> controller.newUser(u));

        assertEquals(HttpStatus.GONE, e.getStatus());
        verify(usuarios, never()).save(any());
    }
}
