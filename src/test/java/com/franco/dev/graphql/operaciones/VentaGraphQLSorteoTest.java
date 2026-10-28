package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.SorteoClienteInput;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.dto.SorteoTicketDto;
import com.franco.dev.service.sorteo.SorteoParticipacionService;
import com.franco.dev.service.sorteo.SorteoParticipacionService.Participacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El paso del sorteo dentro de saveVenta: imprime al final del stream del cliente y nunca lanza. */
class VentaGraphQLSorteoTest {

    @Mock private SorteoParticipacionService sorteoParticipacionService;
    @Mock private ImpresionService impresionService;
    @InjectMocks private VentaGraphQL ventaGraphQL;

    private final List<CobroDetalleInput> detalles = Collections.emptyList();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static Venta venta() {
        Venta v = new Venta();
        v.setId(100L);
        v.setTotalGs(500000d);
        return v;
    }

    private static Participacion participacion(int cantidad) {
        Participacion p = new Participacion();
        p.setSorteoId(4L);
        p.setNombreSorteo("NAVIDAD");
        p.setCodigo("S4-7-100");
        p.setCantidad(cantidad);
        return p;
    }

    @Test
    void sinClienteNoHaceNada() {
        Venta v = venta();
        ventaGraphQL.registrarEImprimirSorteo(v, detalles, null, 3L, "p", "l", null);
        verifyNoInteractions(sorteoParticipacionService, impresionService);
        assertNull(v.getSorteoTickets());
    }

    @Test
    @SuppressWarnings("unchecked")
    void imprimeLosTicketsEnElStreamDelClienteYDevuelveElResumen() {
        Venta v = venta();
        SorteoClienteInput c = new SorteoClienteInput();
        c.setNombre("JUAN");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(sorteoParticipacionService.registrar(v, detalles, c, 3L))
                .thenReturn(Collections.singletonList(participacion(3)));
        when(impresionService.printSorteoTickets(anyList(), any(), any(), any())).thenReturn(true);

        ventaGraphQL.registrarEImprimirSorteo(v, detalles, c, 3L, "impresora", "caja 1", stream);

        ArgumentCaptor<List<SorteoTicketDto>> tickets = ArgumentCaptor.forClass(List.class);
        verify(impresionService).printSorteoTickets(tickets.capture(), eq("impresora"), eq("caja 1"), same(stream));
        assertEquals(3, tickets.getValue().size());
        assertEquals(1, v.getSorteoTickets().size());
        assertEquals("NAVIDAD", v.getSorteoTickets().get(0).getNombreSorteo());
        assertEquals(3, v.getSorteoTickets().get(0).getCantidad());
    }

    @Test
    void sinParticipacionesNoImprime() {
        Venta v = venta();
        SorteoClienteInput c = new SorteoClienteInput();
        when(sorteoParticipacionService.registrar(any(), any(), any(), any())).thenReturn(Collections.emptyList());
        ventaGraphQL.registrarEImprimirSorteo(v, detalles, c, 3L, "p", "l", null);
        verifyNoInteractions(impresionService);
        assertNull(v.getSorteoTickets());
    }

    @Test
    void unErrorDelSorteoNoLanza() {
        Venta v = venta();
        SorteoClienteInput c = new SorteoClienteInput();
        when(sorteoParticipacionService.registrar(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));
        assertDoesNotThrow(() -> ventaGraphQL.registrarEImprimirSorteo(v, detalles, c, 3L, "p", "l", null));
        assertNull(v.getSorteoTickets());
    }
}
