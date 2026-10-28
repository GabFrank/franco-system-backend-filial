package com.franco.dev.graphql.operaciones;

import com.franco.dev.graphql.operaciones.input.SorteoEvaluacionInput;
import com.franco.dev.service.sorteo.SorteoAplicable;
import com.franco.dev.service.sorteo.SorteoLector;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SorteoGraphQLTest {

    private final SorteoLector lector = Mockito.mock(SorteoLector.class);
    private final SorteoGraphQL graphQL = new SorteoGraphQL();

    SorteoGraphQLTest() {
        ReflectionTestUtils.setField(graphQL, "sorteoLector", lector);
    }

    @Test
    void delegaEnElLector() {
        SorteoEvaluacionInput in = new SorteoEvaluacionInput();
        in.setTotalGs(150000.0);
        List<SorteoAplicable> esperado = new ArrayList<>();
        esperado.add(new SorteoAplicable());
        Mockito.when(lector.aplicables(150000.0, null)).thenReturn(esperado);
        assertEquals(esperado, graphQL.sorteosAplicables(in));
    }

    @Test
    void totalNuloDevuelveVacio() {
        assertTrue(graphQL.sorteosAplicables(new SorteoEvaluacionInput()).isEmpty());
        Mockito.verifyNoInteractions(lector);
    }
}
