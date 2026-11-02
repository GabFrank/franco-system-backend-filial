package com.franco.dev.utilitarios.print.output;

import org.junit.jupiter.api.Test;

import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Un fallo al mandar el trabajo a la impresora termina con error en vez de colgar al que escribe (issue #205). */
class PrinterOutputStreamTest {

    private static final Duration LIMITE = Duration.ofSeconds(5);
    /** Mas que el buffer de 1024 bytes del pipe: con el codigo anterior, escribir esto se colgaba. */
    private static final byte[] TICKET = new byte[5000];

    private static PrintService impresoraCon(DocPrintJob job) {
        PrintService ps = mock(PrintService.class);
        when(ps.createPrintJob()).thenReturn(job);
        return ps;
    }

    @Test
    void siElEnvioFallaLaEscrituraTerminaConElMotivo() throws Exception {
        DocPrintJob job = mock(DocPrintJob.class);
        doThrow(new PrintException("Printer is not accepting job.")).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));
        salida.setUncaughtException((t, e) -> { });

        IOException e = assertTimeoutPreemptively(LIMITE,
                () -> assertThrows(IOException.class, () -> salida.write(TICKET)));

        assertEquals("No se pudo imprimir: Printer is not accepting job.", e.getMessage());
    }

    @Test
    void siElEnvioFallaMientrasElQueEscribeEsperaLugarTambienTermina() throws Exception {
        DocPrintJob job = mock(DocPrintJob.class);
        // Falla tarde: para entonces el que escribe ya lleno el buffer y espera lugar.
        doAnswer(inv -> {
            Thread.sleep(300);
            throw new PrintException("Printer is not accepting job.");
        }).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));
        salida.setUncaughtException((t, e) -> { });

        IOException e = assertTimeoutPreemptively(LIMITE,
                () -> assertThrows(IOException.class, () -> salida.write(TICKET)));

        assertEquals("No se pudo imprimir: Printer is not accepting job.", e.getMessage());
    }

    @Test
    void siLaImpresoraVuelveSinLeerLaEscrituraTerminaConError() throws Exception {
        DocPrintJob job = mock(DocPrintJob.class);
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));

        IOException e = assertTimeoutPreemptively(LIMITE,
                () -> assertThrows(IOException.class, () -> salida.write(TICKET)));

        assertTrue(e.getMessage().startsWith("No se pudo imprimir"), e.getMessage());
    }

    @Test
    void unEnvioNormalRecibeTodoElTicket() throws Exception {
        DocPrintJob job = mock(DocPrintJob.class);
        AtomicInteger recibidos = new AtomicInteger();
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            byte[] buffer = new byte[512];
            int n;
            while ((n = in.read(buffer)) != -1) {
                recibidos.addAndGet(n);
            }
            return null;
        }).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));

        assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(TICKET);
            salida.closeYEsperarEnvio(LIMITE.toMillis());
        });

        assertEquals(TICKET.length, recibidos.get());
    }

    @Test
    void unTicketChicoConElEnvioFalladoNoCuelgaAlCerrar() throws Exception {
        DocPrintJob job = mock(DocPrintJob.class);
        doThrow(new PrintException("Printer is not accepting job.")).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));
        salida.setUncaughtException((t, e) -> { });

        assertTimeoutPreemptively(LIMITE, () -> {
            try {
                salida.write(new byte[100]);
                salida.flush();
            } catch (IOException esperable) {
                // si el hilo de impresion ya murio, la escritura avisa; si no, entra en el buffer
            }
            salida.close();
        });
    }
}
