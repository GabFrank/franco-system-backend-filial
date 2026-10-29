package com.franco.dev.utilitarios.print.output;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Pipe hacia un hilo que manda los datos a la impresora (CUPS o socket).
 * <p>
 * Si ese hilo termina sin haber leido, el que escribe esperaria lugar en el pipe para siempre y el
 * request no terminaria (issue #205). Por eso el hilo, al terminar, siempre cierra su punta: el
 * {@code write} pendiente sale con {@link IOException}, y si el envio fallo, con el motivo.
 */
abstract class SalidaConEnvioEnHilo extends PipedOutputStream {

    protected final PipedInputStream pipedInputStream;

    /** Por que fallo el envio, si fallo. Lo escribe el hilo de envio. */
    private volatile Throwable fallaEnvio;
    /** El hilo de envio ya termino y cerro su punta del pipe. */
    private volatile boolean envioTerminado;

    protected SalidaConEnvioEnHilo() throws IOException {
        pipedInputStream = new PipedInputStream();
        super.connect(pipedInputStream);
    }

    /** Lo llama el hilo de envio cuando el envio falla, antes de terminar. */
    protected void registrarFalla(Throwable causa) {
        fallaEnvio = causa;
    }

    /** Lo llama el hilo de envio al terminar, siempre (en un finally). */
    protected void terminarEnvio() {
        envioTerminado = true;
        try {
            pipedInputStream.close();
        } catch (IOException ignorada) {
            // cerrar un PipedInputStream no falla
        }
    }

    @Override
    public void write(int b) throws IOException {
        try {
            super.write(b);
        } catch (IOException e) {
            throw conMotivo(e);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            super.write(b, off, len);
        } catch (IOException e) {
            throw conMotivo(e);
        }
    }

    /** El error del pipe con el motivo real, si el envio fallo o termino antes de tiempo. */
    private IOException conMotivo(IOException e) {
        Throwable causa = fallaEnvio;
        if (causa != null) {
            return new IOException("No se pudo imprimir: " + causa.getMessage(), causa);
        }
        if (envioTerminado) {
            return new IOException("No se pudo imprimir: la impresora no tomo el trabajo", e);
        }
        return e;
    }
}
