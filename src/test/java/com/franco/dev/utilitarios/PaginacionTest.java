package com.franco.dev.utilitarios;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** #611: page/size nulos o invalidos ya no tiran excepcion; size se recorta al maximo. */
class PaginacionTest {

    @Test
    void nulosDanLaPrimeraPaginaPorDefecto() {
        Pageable p = Paginacion.of(null, null);
        assertEquals(0, p.getPageNumber());
        assertEquals(Paginacion.SIZE_POR_DEFECTO, p.getPageSize());
    }

    @Test
    void pageSinSizeYSizeSinPage() {
        assertEquals(3, Paginacion.of(3, null).getPageNumber());
        assertEquals(Paginacion.SIZE_POR_DEFECTO, Paginacion.of(3, null).getPageSize());
        assertEquals(0, Paginacion.of(null, 10).getPageNumber());
        assertEquals(10, Paginacion.of(null, 10).getPageSize());
    }

    @Test
    void negativosYCeroSeCorrigen() {
        Pageable p = Paginacion.of(-1, 0);
        assertEquals(0, p.getPageNumber());
        assertEquals(Paginacion.SIZE_POR_DEFECTO, p.getPageSize());
    }

    @Test
    void valoresValidosSeRespetan() {
        Pageable p = Paginacion.of(2, 10, Sort.by("id"));
        assertEquals(2, p.getPageNumber());
        assertEquals(10, p.getPageSize());
        assertEquals(Sort.by("id"), p.getSort());
    }

    @Test
    void sizeMayorAlMaximoSeRecorta() {
        assertEquals(Paginacion.SIZE_MAXIMO, Paginacion.of(0, 5000).getPageSize());
    }
}
