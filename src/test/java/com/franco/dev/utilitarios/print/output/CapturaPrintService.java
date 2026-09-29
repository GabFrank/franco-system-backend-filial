package com.franco.dev.utilitarios.print.output;

import javax.imageio.ImageIO;
import javax.print.*;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintJobAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.event.PrintJobAttributeListener;
import javax.print.event.PrintJobListener;
import javax.print.event.PrintServiceAttributeListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Impresora falsa para tests: se queda con los bytes que le manda {@link PrinterOutputStream}, que
 * es exactamente lo que hoy le llega a la impresora real por CUPS. Sirve para comparar el camino
 * "imprime el servidor" contra el camino "devuelve los bytes al cliente" de un mismo ticket.
 */
public class CapturaPrintService implements PrintService {

    private final ByteArrayOutputStream capturado = new ByteArrayOutputStream();
    private final CountDownLatch terminado = new CountDownLatch(1);

    /** Espera a que el hilo de {@link PrinterOutputStream} termine de leer y devuelve lo impreso. */
    public byte[] bytes() throws InterruptedException {
        if (!terminado.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("La impresora falsa no recibio el trabajo completo");
        }
        return capturado.toByteArray();
    }

    /** Un logo.png cualquiera en {@code dir}, porque los tickets lo leen del disco. */
    public static String directorioConLogo(File dir) throws IOException {
        BufferedImage logo = new BufferedImage(300, 150, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 300; x += 7) {
            for (int y = 0; y < 150; y++) {
                logo.setRGB(x, y, 0xFFFFFF);
            }
        }
        ImageIO.write(logo, "png", new File(dir, "logo.png"));
        return dir.getAbsolutePath() + File.separator;
    }

    @Override
    public DocPrintJob createPrintJob() {
        final PrintService self = this;
        return new DocPrintJob() {
            @Override public PrintService getPrintService() { return self; }
            @Override public PrintJobAttributeSet getAttributes() { return null; }
            @Override public void addPrintJobListener(PrintJobListener l) { }
            @Override public void removePrintJobListener(PrintJobListener l) { }
            @Override public void addPrintJobAttributeListener(PrintJobAttributeListener l, PrintJobAttributeSet a) { }
            @Override public void removePrintJobAttributeListener(PrintJobAttributeListener l) { }

            @Override
            public void print(Doc doc, PrintRequestAttributeSet attributes) throws PrintException {
                try (InputStream in = doc.getStreamForBytes()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        capturado.write(buf, 0, n);
                    }
                } catch (IOException e) {
                    throw new PrintException(e);
                } finally {
                    terminado.countDown();
                }
            }
        };
    }

    @Override public String getName() { return "CAPTURA"; }
    @Override public void addPrintServiceAttributeListener(PrintServiceAttributeListener l) { }
    @Override public void removePrintServiceAttributeListener(PrintServiceAttributeListener l) { }
    @Override public PrintServiceAttributeSet getAttributes() { return null; }
    @Override public <T extends PrintServiceAttribute> T getAttribute(Class<T> category) { return null; }
    @Override public DocFlavor[] getSupportedDocFlavors() { return new DocFlavor[0]; }
    @Override public boolean isDocFlavorSupported(DocFlavor flavor) { return true; }
    @Override public Class<?>[] getSupportedAttributeCategories() { return new Class<?>[0]; }
    @Override public boolean isAttributeCategorySupported(Class<? extends Attribute> category) { return false; }
    @Override public Object getDefaultAttributeValue(Class<? extends Attribute> category) { return null; }
    @Override public Object getSupportedAttributeValues(Class<? extends Attribute> c, DocFlavor f, AttributeSet a) { return null; }
    @Override public boolean isAttributeValueSupported(Attribute attrval, DocFlavor flavor, AttributeSet attributes) { return false; }
    @Override public AttributeSet getUnsupportedAttributes(DocFlavor flavor, AttributeSet attributes) { return null; }
    @Override public ServiceUIFactory getServiceUIFactory() { return null; }
}
