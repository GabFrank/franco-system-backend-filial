package com.franco.dev.utilitarios.print.output;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pipe hacia un hilo que manda los datos a la impresora (CUPS o socket).
 * <p>
 * Si ese hilo termina sin haber leido, el que escribe esperaria lugar en el pipe para siempre y el
 * request no terminaria (issue #205). Por eso el hilo, al terminar, siempre cierra su punta: el
 * {@code write} pendiente sale con {@link IOException}, y si el envio fallo, con el motivo.
 * <p>
 * Un ticket chico entra entero en el buffer del pipe y ningun {@code write} se entera de la falla
 * (issue #213). Quien necesita saberlo llama a {@link #esperarEnvio()}: entonces {@link #close()}
 * espera al hilo de envio y avisa si fallo. Por defecto no espera: la venta del PDV imprime dentro de
 * su transaccion y ahi la falla solo va al log.
 * <p>
 * Si el armado del ticket falla y nadie cierra la salida, el hilo de envio quedaria leyendo para
 * siempre. Un vigia lo interrumpe tras {@link #abandonoMillis} sin actividad: el envio falla sin llegar
 * a CUPS (cerrar la salida en cambio imprimiria medio ticket).
 */
abstract class SalidaConEnvioEnHilo extends PipedOutputStream {

    private static final int ABIERTA = 0;
    private static final int CERRADA = 1;
    private static final int CANCELADA = 2;
    private static final ScheduledThreadPoolExecutor VIGIA = crearVigia();
    /** Cada cuanto revisa el vigia. shortcut: estatico y mutable solo para que los tests lo bajen; no se toca en produccion. */
    static volatile long revisionMillis = 1000;

    protected final PipedInputStream pipedInputStream;

    /** Cuanto espera {@link #close()} al hilo de envio, si se pidio esperar. CUPS tarda menos de 1 s. */
    long esperaEnvioMillis = 10_000;
    /** Sin escribir ni cerrar durante este tiempo, el envio se cancela. Armar un ticket tarda milisegundos. */
    volatile long abandonoMillis = 30_000;

    /** Por que fallo el envio: gana la primera (el vigia la anota antes de interrumpir). */
    private final AtomicReference<Throwable> fallaEnvio = new AtomicReference<>();
    /** Llega a cero cuando el hilo de envio termino y cerro su punta del pipe. */
    private final CountDownLatch envioTerminado = new CountDownLatch(1);
    /** ABIERTA hasta que gana el cierre (CERRADA) o el vigia (CANCELADA). */
    private final AtomicInteger estado = new AtomicInteger(ABIERTA);
    /** Ya se espero al envio una vez; va aparte del estado: un close() comun no le quita la espera al pagare. */
    private final AtomicBoolean esperado = new AtomicBoolean();
    private volatile boolean esperar;

    private volatile long ultimaActividad = System.nanoTime();
    private volatile boolean escribiendo;
    private volatile Thread hiloEnvio;
    private volatile ScheduledFuture<?> revision;

    protected SalidaConEnvioEnHilo() throws IOException {
        pipedInputStream = new PipedInputStream();
        super.connect(pipedInputStream);
    }

    private static ScheduledThreadPoolExecutor crearVigia() {
        ScheduledThreadPoolExecutor vigia = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "vigia-impresion");
            t.setDaemon(true);
            return t;
        });
        vigia.setRemoveOnCancelPolicy(true);
        return vigia;
    }

    /** Hace que {@link #close()} espere al envio y tire {@link IOException} si fallo. */
    public void esperarEnvio() {
        esperar = true;
    }

    /** Lo llama la subclase al crear el hilo de envio: desde ahi corre el plazo de abandono. */
    protected void vigilarEnvio(Thread hilo) {
        hiloEnvio = hilo;
        programarRevision();
    }

    private void programarRevision() {
        revision = VIGIA.schedule(this::revisarAbandono, Math.min(revisionMillis, abandonoMillis), TimeUnit.MILLISECONDS);
    }

    private void revisarAbandono() {
        if (estado.get() != ABIERTA || envioTerminado.getCount() == 0) {
            return;
        }
        // Un write en curso puede estar esperando lugar porque la impresora lee lento: no es abandono.
        if (escribiendo || System.nanoTime() - ultimaActividad < TimeUnit.MILLISECONDS.toNanos(abandonoMillis)) {
            programarRevision();
            return;
        }
        if (estado.compareAndSet(ABIERTA, CANCELADA)) {
            registrarFalla(new IOException("el ticket quedo a medio armar (" + abandonoMillis / 1000
                    + " s sin escribir ni cerrar)"));
            hiloEnvio.interrupt();
        }
    }

    /** Lo llama el hilo de envio cuando el envio falla, antes de terminar. */
    protected void registrarFalla(Throwable causa) {
        fallaEnvio.compareAndSet(null, causa);
    }

    /** Lo llama el hilo de envio al terminar, siempre (en un finally). */
    protected void terminarEnvio() {
        envioTerminado.countDown();
        ScheduledFuture<?> r = revision;
        if (r != null) {
            r.cancel(false);
        }
        try {
            pipedInputStream.close();
        } catch (IOException ignorada) {
            // cerrar un PipedInputStream no falla
        }
    }

    @Override
    public void write(int b) throws IOException {
        empezarEscritura();
        try {
            super.write(b);
        } catch (IOException e) {
            throw conMotivo(e);
        } finally {
            terminarEscritura();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        empezarEscritura();
        try {
            super.write(b, off, len);
        } catch (IOException e) {
            throw conMotivo(e);
        } finally {
            terminarEscritura();
        }
    }

    private void empezarEscritura() {
        escribiendo = true;
        ultimaActividad = System.nanoTime();
    }

    private void terminarEscritura() {
        ultimaActividad = System.nanoTime();
        escribiendo = false;
    }

    /**
     * Cierra el pipe. Con {@link #esperarEnvio()}, ademas espera a que el hilo de envio termine y avisa
     * si fallo; solo se espera una vez (los llamadores suelen cerrar dos veces).
     */
    @Override
    public void close() throws IOException {
        cerrar(esperar ? esperaEnvioMillis : -1);
    }

    /**
     * Cierra y, si {@code esperaMillis >= 0}, espera hasta ese tiempo a que el envio termine.
     *
     * @exception IOException si el ticket quedo a medio armar, el envio fallo o no termino a tiempo.
     */
    void cerrar(long esperaMillis) throws IOException {
        if (estado.compareAndSet(CANCELADA, CERRADA)) {
            // Gano el vigia: no se entrega el fin de datos, imprimiria medio ticket antes de la interrupcion.
            Throwable causa = fallaEnvio.get();
            throw new IOException("No se pudo imprimir: "
                    + (causa != null ? causa.getMessage() : "el ticket quedo a medio armar"), causa);
        }
        // El estado se gana siempre, tambien sin espera (si no, el vigia cortaria un ticket ya completo), y solo
        // quien lo gana entrega el fin de datos: un segundo cierre tras una cancelacion no lo entrega.
        if (estado.compareAndSet(ABIERTA, CERRADA)) {
            super.close();
        }
        if (esperaMillis < 0 || !esperado.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!envioTerminado.await(esperaMillis, TimeUnit.MILLISECONDS)) {
                // El hilo sigue: el ticket puede salir igual, de ahi el aviso antes de reintentar.
                throw new IOException("No se pudo confirmar la impresion en " + esperaMillis / 1000
                        + " s: revisar la impresora antes de reintentar");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Se interrumpio la espera de la impresion");
        }
        Throwable causa = fallaEnvio.get();
        if (causa != null) {
            throw new IOException("No se pudo imprimir: " + causa.getMessage(), causa);
        }
    }

    /** El error del pipe con el motivo real, si el envio fallo o termino antes de tiempo. */
    private IOException conMotivo(IOException e) {
        Throwable causa = fallaEnvio.get();
        if (causa != null) {
            return new IOException("No se pudo imprimir: " + causa.getMessage(), causa);
        }
        if (envioTerminado.getCount() == 0) {
            return new IOException("No se pudo imprimir: la impresora no tomo el trabajo", e);
        }
        return e;
    }
}
