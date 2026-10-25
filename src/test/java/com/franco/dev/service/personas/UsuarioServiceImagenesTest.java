package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.utils.ImageService;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** La foto de perfil se busca solo por nombre de archivo: una ruta en `imagenes` no se lee ni se escribe. */
class UsuarioServiceImagenesTest {

    @Test
    void unaRutaEnImagenesNoSeLeeNiSeDescarga() {
        UsuarioService s = new ObjenesisStd().newInstance(UsuarioService.class);
        UsuarioRepository repo = mock(UsuarioRepository.class);
        ImageService images = mock(ImageService.class);
        CentralPersonasIntegrationService central = mock(CentralPersonasIntegrationService.class);
        ReflectionTestUtils.setField(s, "repository", repo);
        ReflectionTestUtils.setField(s, "imageService", images);
        ReflectionTestUtils.setField(s, "centralPersonasIntegrationService", central);

        Persona p = new Persona();
        p.setId(7L);
        p.setImagenes("/opt/frc-filial/.env,../../etc/passwd,..\\..\\x,12_perfil1.png");
        Usuario u = new Usuario();
        u.setId(5L);
        u.setPersona(p);
        when(repo.findById(5L)).thenReturn(Optional.of(u));
        when(images.getImageWithMediaType(eq("12_perfil1.png"), anyString())).thenReturn("data:image/png;base64,AA");

        assertEquals(1, s.getUserImages(5L, "perfil").size());
        verify(images, times(1)).getImageWithMediaType(anyString(), anyString());
        verify(central, never()).downloadImage(anyLong());
    }

    @Test
    void nombreDeArchivo() {
        assertTrue(UsuarioService.esNombreDeArchivo("12_perfil1.png"));
        assertTrue(!UsuarioService.esNombreDeArchivo("/etc/passwd"));
        assertTrue(!UsuarioService.esNombreDeArchivo("..\\x"));
    }
}
