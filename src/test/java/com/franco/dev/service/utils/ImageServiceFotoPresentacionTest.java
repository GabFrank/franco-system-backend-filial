package com.franco.dev.service.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El escritorio manda las mismas consultas al central y al filial: los campos de foto por tamano
 * tienen que resolver igual aca (issue #263 del central), con lo que haya en el disco del filial.
 */
class ImageServiceFotoPresentacionTest {

    private Path dir;

    private ImageService service;

    @BeforeEach
    void setUp() throws Exception {
        dir = Files.createTempDirectory("fotos-presentacion");
        service = new ImageService();
        service.imagePresentaciones = dir + "/";
        service.imagePresentacionesThumbPath = dir + "/thumbnails/";
        service.imagePresentacionesMedianaPath = dir + "/medianas/";
    }

    @Test
    void cadaTamanoLeeSuArchivo() throws Exception {
        escribir("7.jpg", "original"); escribir("medianas/7.jpg", "mediana"); escribir("thumbnails/7.jpg", "miniatura");

        assertEquals("miniatura", contenido(service.fotoPresentacion(7L, false)));
        assertEquals("mediana", contenido(service.fotoPresentacion(7L, true)));
    }

    @Test
    void sinElTamanoPedidoCaeAlSiguienteMasGrande() throws Exception {
        escribir("7.jpg", "original");

        assertEquals("original", contenido(service.fotoPresentacion(7L, false)));
        assertEquals("original", contenido(service.fotoPresentacion(7L, true)));
    }

    @Test
    void laMedianaNuncaDevuelveLaMiniatura() throws Exception {
        escribir("thumbnails/7.jpg", "miniatura");

        assertNull(service.fotoPresentacion(7L, true));
    }

    @Test
    void sinFotoDevuelveNull() {
        assertNull(service.fotoPresentacion(7L, false));
        assertNull(service.fotoPresentacion(null, false));
    }

    private void escribir(String relativo, String texto) throws Exception {
        Path destino = dir.resolve(relativo);
        Files.createDirectories(destino.getParent());
        Files.write(destino, texto.getBytes(StandardCharsets.UTF_8));
    }

    private String contenido(String dataUri) {
        assertNotNull(dataUri);
        return new String(Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1)), StandardCharsets.UTF_8);
    }
}
