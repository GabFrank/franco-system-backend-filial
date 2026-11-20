package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** #148: pdv_caja.estado no lo escribe nadie (NULL en todas), asi que filtrar por el daba 0 filas sin avisar. */
class PdvCajaServiceFiltroEstadoTest {

    @Mock private PdvCajaRepository repository;
    @InjectMocks private PdvCajaService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void conEstadoRechazaSinConsultar() {
        assertThrows(GraphQLException.class, () -> service.findAllWithFilters(null, PdvCajaEstado.EN_PROCESO,
                null, null, null, null, 24L, null, PageRequest.of(0, 10)));
        verifyNoInteractions(repository);
    }

    @Test
    void sinEstadoConsultaComoAntes() {
        PageRequest page = PageRequest.of(0, 10);
        service.findAllWithFilters(5L, null, 2L, 3L, null, null, 24L, true, page);
        verify(repository).findAllWithFilters(5L, 2L, 3L, null, null, 24L, true, page);
    }
}
