package com.franco.dev.service.impresion;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.domain.empresarial.enums.TipoConexion;
import com.franco.dev.service.empresarial.ImpresoraService;
import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * La impresion por impresora del registro (prueba de impresora, «Imprimir en la sucursal») avisa cuando un
 * ticket chico no sale (#213): antes entraba entero en el pipe y se informaba como impreso.
 */
class ImpresoraPrintServiceEsperaTest {

    private static final String COLA_RECHAZA = "zz_rechaza_213";
    private static final String COLA_ACEPTA = "zz_acepta_213";
    private static final byte[] TICKET = "PRUEBA DE IMPRESION\n".getBytes();

    private static PrintService rechaza;
    private static CapturaPrintService acepta;

    @Mock private ImpresoraService impresoraService;
    @Spy private ImpresoraPrintService impresoraPrintService = new ImpresoraPrintService();
    @InjectMocks private PrintRouterService router;

    /**
     * Colas registradas en el JVM: abrirOutput las busca por nombre como a las de CUPS. El registro no tiene baja y
     * getPrintServiceByName tambien busca por «contiene»: nombres zz_..._213 para que otro test no las encuentre.
     */
    @BeforeAll
    static void registrarColas() throws PrintException {
        // CUPS que lee todo el ticket y despues lo rechaza: el ticket entra en el buffer sin que write se entere.
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            while (in.read() != -1) {
                // consume hasta el cierre
            }
            throw new PrintException("Printer is not accepting job.");
        }).when(job).print(any(), any());
        rechaza = mock(PrintService.class);
        when(rechaza.getName()).thenReturn(COLA_RECHAZA);
        when(rechaza.isDocFlavorSupported(any())).thenReturn(true);
        when(rechaza.createPrintJob()).thenReturn(job);
        acepta = new CapturaPrintService() {
            @Override public String getName() { return COLA_ACEPTA; }
        };
        assertTrue(PrintServiceLookup.registerService(rechaza));
        assertTrue(PrintServiceLookup.registerService(acepta));
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private static Impresora cola(String nombre) {
        Impresora impresora = new Impresora();
        impresora.setId(1L);
        impresora.setConexion(TipoConexion.CUPS);
        impresora.setColaCups(nombre);
        return impresora;
    }

    @Test
    void unTicketChicoQueLaColaRechazaDevuelveFalse() throws Exception {
        assertFalse(router.imprimirLocal(cola(COLA_RECHAZA), TICKET));
        // Llego a la cola: el false es por el rechazo y no por una cola no encontrada.
        verify(rechaza).createPrintJob();
    }

    @Test
    void unTicketQueLaColaAceptaDevuelveTrue() throws Exception {
        assertTrue(router.imprimirLocal(cola(COLA_ACEPTA), TICKET));
        assertArrayEquals(TICKET, acepta.bytes());
    }
}
