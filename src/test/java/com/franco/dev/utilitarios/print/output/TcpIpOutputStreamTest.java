package com.franco.dev.utilitarios.print.output;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Una impresora de red que no atiende hace fallar la escritura en vez de colgarla (issue #205). */
class TcpIpOutputStreamTest {

    private static final Duration LIMITE = Duration.ofSeconds(5);
    private static final byte[] TICKET = new byte[5000];

    @Test
    void siLaImpresoraDeRedNoAtiendeLaEscrituraTerminaConError() throws Exception {
        int puertoCerrado;
        try (ServerSocket s = new ServerSocket(0)) {
            puertoCerrado = s.getLocalPort();
        }
        TcpIpOutputStream salida = new TcpIpOutputStream("127.0.0.1", puertoCerrado);
        salida.setUncaughtException((t, e) -> { });

        IOException e = assertTimeoutPreemptively(LIMITE,
                () -> assertThrows(IOException.class, () -> salida.write(TICKET)));

        assertTrue(e.getMessage().startsWith("No se pudo imprimir"), e.getMessage());
    }

    @Test
    void unaImpresoraDeRedQueAtiendeRecibeTodoElTicket() throws Exception {
        AtomicInteger recibidos = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread lector = new Thread(() -> {
                try (Socket s = server.accept(); InputStream in = s.getInputStream()) {
                    byte[] b = new byte[512];
                    int n;
                    while ((n = in.read(b)) != -1) recibidos.addAndGet(n);
                } catch (IOException ignorada) {
                }
            });
            lector.start();
            TcpIpOutputStream salida = new TcpIpOutputStream("127.0.0.1", server.getLocalPort());

            assertTimeoutPreemptively(LIMITE, () -> {
                salida.write(TICKET);
                salida.close();
                lector.join(LIMITE.toMillis());
            });
        }
        assertEquals(TICKET.length, recibidos.get());
    }

    @Test
    void conEsperarEnvioUnTicketChicoAUnaImpresoraDeRedQueNoAtiendeAvisa() throws Exception {
        int puertoCerrado;
        try (ServerSocket s = new ServerSocket(0)) {
            puertoCerrado = s.getLocalPort();
        }
        TcpIpOutputStream salida = new TcpIpOutputStream("127.0.0.1", puertoCerrado);
        salida.setUncaughtException((t, e) -> { });
        salida.esperarEnvio();

        // Segun quien gane la carrera, avisa el write (el hilo ya cerro el pipe) o el close (#213).
        IOException e = assertTimeoutPreemptively(LIMITE, () -> assertThrows(IOException.class, () -> {
            salida.write(new byte[100]);
            salida.close();
        }));

        assertTrue(e.getMessage().startsWith("No se pudo imprimir"), e.getMessage());
    }
}
