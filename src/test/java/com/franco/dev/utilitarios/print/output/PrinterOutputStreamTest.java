package com.franco.dev.utilitarios.print.output;

import org.junit.jupiter.api.Test;

import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** Un job que lee todo el ticket y despues falla: el ticket chico entra en el buffer sin que write se entere (#213). */
    private static DocPrintJob jobQueLeeYFalla() throws PrintException {
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            while (in.read() != -1) {
                // consume hasta el cierre
            }
            throw new PrintException("Printer is not accepting job.");
        }).when(job).print(any(), any());
        return job;
    }

    @Test
    void sinEsperarEnvioUnTicketChicoFalladoCierraComoSiempre() throws Exception {
        // Es lo que usa la venta del PDV: imprime dentro de su transaccion y no espera.
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLeeYFalla()));
        salida.setUncaughtException((t, e) -> { });

        assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(new byte[100]);
            salida.close();
        });
    }

    @Test
    void conEsperarEnvioUnTicketChicoFalladoAvisaAlCerrar() throws Exception {
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLeeYFalla()));
        salida.setUncaughtException((t, e) -> { });
        salida.esperarEnvio();

        IOException e = assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(new byte[100]);
            return assertThrows(IOException.class, salida::close);
        });

        assertEquals("No se pudo imprimir: Printer is not accepting job.", e.getMessage());
    }

    @Test
    void conEsperarEnvioElSegundoCierreNoRepiteLaFalla() throws Exception {
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLeeYFalla()));
        salida.setUncaughtException((t, e) -> { });
        salida.esperarEnvio();

        assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(new byte[100]);
            assertThrows(IOException.class, salida::close);
            salida.close();
        });
    }

    @Test
    void conEsperarEnvioSiElEnvioNoTerminaAvisaAlVencerLaEspera() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            liberar.await();
            return null;
        }).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));
        salida.esperarEnvio();
        salida.esperaEnvioMillis = 300;
        try {
            IOException e = assertTimeoutPreemptively(LIMITE, () -> {
                salida.write(new byte[100]);
                return assertThrows(IOException.class, salida::close);
            });

            assertTrue(e.getMessage().startsWith("No se pudo confirmar la impresion"), e.getMessage());
        } finally {
            liberar.countDown();
        }
    }

    @Test
    void conEsperarEnvioUnEnvioNormalTerminaAntesDeQueVuelvaElCierre() throws Exception {
        AtomicBoolean enviado = new AtomicBoolean();
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            while (in.read() != -1) {
                // consume hasta el cierre
            }
            Thread.sleep(200);
            enviado.set(true);
            return null;
        }).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));
        salida.esperarEnvio();

        assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(new byte[100]);
            salida.close();
        });

        assertTrue(enviado.get());
    }

    @Test
    void closeYEsperarEnvioAvisaLaFalla() throws Exception {
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLeeYFalla()));
        salida.setUncaughtException((t, e) -> { });

        IOException e = assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(new byte[100]);
            return assertThrows(IOException.class, () -> salida.closeYEsperarEnvio(LIMITE.toMillis()));
        });

        assertEquals("No se pudo imprimir: Printer is not accepting job.", e.getMessage());
    }

    // ---- Vigia de tickets a medio armar (etapa 4b) ----

    private long revisionOriginal;

    @org.junit.jupiter.api.BeforeEach
    void guardarRevision() {
        revisionOriginal = SalidaConEnvioEnHilo.revisionMillis;
    }

    @org.junit.jupiter.api.AfterEach
    void restaurarRevision() {
        SalidaConEnvioEnHilo.revisionMillis = revisionOriginal;
    }

    /** Un job como el de CUPS: lee todo con una pausa por bloque; si la lectura falla, el envio falla (no imprime). */
    private static DocPrintJob jobQueLee(AtomicInteger recibidos, AtomicBoolean impreso, long pausaPorBloqueMillis)
            throws PrintException {
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            byte[] buffer = new byte[100];
            int n;
            try {
                while ((n = in.read(buffer)) != -1) {
                    recibidos.addAndGet(n);
                    Thread.sleep(pausaPorBloqueMillis);
                }
            } catch (IOException | InterruptedException e) {
                throw new PrintException(e.toString());
            }
            impreso.set(true);
            return null;
        }).when(job).print(any(), any());
        return job;
    }

    private static PrinterOutputStream salida(DocPrintJob job, long abandonoMillis, boolean esperar) throws IOException {
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));
        salida.setUncaughtException((t, e) -> { });
        salida.abandonoMillis = abandonoMillis;
        if (esperar) {
            salida.esperarEnvio();
        }
        return salida;
    }

    @Test
    void sinEsperarUnTicketAMediasSinCerrarCancelaElEnvio() throws Exception {
        AtomicBoolean impreso = new AtomicBoolean();
        PrinterOutputStream salida = salida(jobQueLee(new AtomicInteger(), impreso, 0), 300, false);

        salida.write(new byte[100]); // el armado falla aca y nadie cierra
        salida.threadPrint.join(LIMITE.toMillis());

        assertFalse(salida.threadPrint.isAlive());
        assertFalse(impreso.get());
        IOException e = assertTimeoutPreemptively(LIMITE, () -> assertThrows(IOException.class, salida::close));
        assertTrue(e.getMessage().startsWith("No se pudo imprimir: el ticket quedo a medio armar"), e.getMessage());
        salida.close(); // el segundo cierre no entrega el fin de datos ni tira
    }

    @Test
    void conEsperarUnTicketAMediasSinCerrarCancelaElEnvio() throws Exception {
        PrinterOutputStream salida = salida(jobQueLee(new AtomicInteger(), new AtomicBoolean(), 0), 300, true);

        salida.write(new byte[100]);
        salida.threadPrint.join(LIMITE.toMillis());

        assertFalse(salida.threadPrint.isAlive());
        IOException e = assertThrows(IOException.class, salida::close);
        assertTrue(e.getMessage().startsWith("No se pudo imprimir: el ticket quedo a medio armar"), e.getMessage());
    }

    @Test
    void unaSalidaNuncaEscritaNiCerradaTambienSeCancela() throws Exception {
        PrinterOutputStream salida = salida(jobQueLee(new AtomicInteger(), new AtomicBoolean(), 0), 300, false);

        salida.threadPrint.join(LIMITE.toMillis());

        assertFalse(salida.threadPrint.isAlive());
    }

    @Test
    void sinEsperarUnaVentaCerradaConUnEnvioLentoNoSeCancela() throws Exception {
        // Ticket de venta: se cierra enseguida y la impresora tarda mas que el plazo en leerlo.
        SalidaConEnvioEnHilo.revisionMillis = 50;
        AtomicInteger recibidos = new AtomicInteger();
        AtomicBoolean impreso = new AtomicBoolean();
        PrinterOutputStream salida = salida(jobQueLee(recibidos, impreso, 40), 300, false);

        assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(new byte[800]); // entra en el buffer
            salida.close();
        });
        salida.threadPrint.join(LIMITE.toMillis());

        assertTrue(impreso.get());
        assertEquals(800, recibidos.get());
    }

    @Test
    void unaEscrituraQueEsperaAUnaImpresoraLentaNoSeCancela() throws Exception {
        AtomicInteger recibidos = new AtomicInteger();
        // 5000 bytes de a 100 cada 50 ms: el write queda bloqueado ~2,5 s, mucho mas que el plazo.
        PrinterOutputStream salida = salida(jobQueLee(recibidos, new AtomicBoolean(), 50), 300, true);

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            salida.write(TICKET);
            salida.close();
        });

        assertEquals(TICKET.length, recibidos.get());
    }

    @Test
    void sinEsperarElCierreVuelveEnseguidaConUnEnvioBloqueado() throws Exception {
        // Guarda del lock por lote: dentro de la venta el cierre no espera a la impresora.
        CountDownLatch liberar = new CountDownLatch(1);
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            liberar.await();
            return null;
        }).when(job).print(any(), any());
        PrinterOutputStream salida = salida(job, 30_000, false);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                salida.write(new byte[100]);
                salida.close();
            });
        } finally {
            liberar.countDown();
        }
    }

    @Test
    void closeYEsperarEnvioEsperaAunqueAntesHuboUnCierreComun() throws Exception {
        AtomicBoolean impreso = new AtomicBoolean();
        PrinterOutputStream salida = salida(jobQueLee(new AtomicInteger(), impreso, 100), 30_000, false);

        salida.write(new byte[300]);
        salida.close();
        salida.closeYEsperarEnvio(LIMITE.toMillis());

        assertTrue(impreso.get());
    }

    @Test
    void closeYEsperarEnvioSobreUnaSalidaCanceladaAvisa() throws Exception {
        PrinterOutputStream salida = salida(jobQueLee(new AtomicInteger(), new AtomicBoolean(), 0), 300, false);

        salida.write(new byte[100]);
        salida.threadPrint.join(LIMITE.toMillis());

        IOException e = assertThrows(IOException.class, () -> salida.closeYEsperarEnvio(LIMITE.toMillis()));
        assertTrue(e.getMessage().contains("a medio armar"), e.getMessage());
    }

    @Test
    void elVigiaYElCierreNuncaSeContradicen() throws Exception {
        SalidaConEnvioEnHilo.revisionMillis = 1; // el vigia revisa cada 1 ms y el plazo es 1 ms: compiten con el cierre
        for (int i = 0; i < 200; i++) {
            AtomicBoolean impreso = new AtomicBoolean();
            PrinterOutputStream salida = salida(jobQueLee(new AtomicInteger(), impreso, 0), 1, true);

            salida.write(new byte[100]);
            boolean cerroBien;
            try {
                salida.close();
                cerroBien = true;
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("a medio armar"), e.getMessage());
                cerroBien = false;
            }
            salida.threadPrint.join(LIMITE.toMillis());

            // Lo que se informa coincide con lo que paso: nunca «impreso» sin imprimir ni al reves.
            assertEquals(cerroBien, impreso.get(), "vuelta " + i);
        }
    }

    @Test
    void alTerminarLosEnviosElVigiaNoQuedaConTareas() throws Exception {
        // Se mira la revision de cada salida y no la cola del vigia, que comparten todos los tests de la JVM.
        java.lang.reflect.Field campo = SalidaConEnvioEnHilo.class.getDeclaredField("revision");
        campo.setAccessible(true);
        for (int i = 0; i < 20; i++) {
            PrinterOutputStream salida = salida(jobQueLee(new AtomicInteger(), new AtomicBoolean(), 0), 30_000, false);
            salida.write(new byte[100]);
            salida.close();
            salida.threadPrint.join(LIMITE.toMillis());
            // Una revision que el vigia reprogramo justo al terminar el envio corre una vez mas y no vuelve.
            long limite = System.currentTimeMillis() + 3000;
            java.util.concurrent.ScheduledFuture<?> revision = (java.util.concurrent.ScheduledFuture<?>) campo.get(salida);
            while (!revision.isDone() && System.currentTimeMillis() < limite) {
                Thread.sleep(20);
                revision = (java.util.concurrent.ScheduledFuture<?>) campo.get(salida);
            }
            assertTrue(revision.isDone(), "la revision sigue programada");
        }
    }
}
