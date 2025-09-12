package com.franco.dev.utilitarios.print.output;/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

import java.awt.print.PrinterJob;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;

/**
 * Supply OutputStream to the printer.
 * <p>
 * PrinterOutputStream send data directing to the printer. The instance cannot
 * be reused and the last command should be <code>close()</code>, after that,
 * you need to create another instance to send data to the printer.
 */
public class PrinterOutputStream extends PipedOutputStream {

    protected final PipedInputStream pipedInputStream;
    protected final Thread threadPrint;

    /**
     * creates one instance of PrinterOutputStream.
     * <p>
     * Create one print based on print service. Start print job linked (this)
     * output stream.
     *
     * @param printService value used to create the printer job
     * @exception IOException if an I/O error occurs.
     * @see #getPrintServiceByName(java.lang.String)
     * @see #getDefaultPrintService()
     */
    public PrinterOutputStream(PrintService printService) throws IOException {

        UncaughtExceptionHandler uncaughtException = (Thread t, Throwable e) -> {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, e.getMessage(),e);
        };

        pipedInputStream = new PipedInputStream();
        super.connect(pipedInputStream);

        Runnable runnablePrint = () -> {
            try {
                DocFlavor df = DocFlavor.INPUT_STREAM.AUTOSENSE;
                Doc d = new SimpleDoc(pipedInputStream, df, null);

                DocPrintJob job = printService.createPrintJob();
                job.print(d, null);
            } catch (PrintException ex) {
                throw new RuntimeException(ex);
            }
        };

        threadPrint = new Thread(runnablePrint);
        threadPrint.setUncaughtExceptionHandler(uncaughtException);
        threadPrint.start();
    }

    /**
     * creates one instance of PrinterOutputStream with default print service.
     * <p>
     * @exception IOException if an I/O error occurs.
     * @see #PrinterOutputStream(javax.print.PrintService)
     * @see #getDefaultPrintService()
     */
    public PrinterOutputStream() throws IOException {
        this(getDefaultPrintService());
    }

    /**
     * Set UncaughtExceptionHandler to make special error treatment.
     * <p>
     * Make special treatment of errors on your code.
     *
     * @param uncaughtException used on (another thread) print.
     */
    public void setUncaughtException(UncaughtExceptionHandler uncaughtException) {
        threadPrint.setUncaughtExceptionHandler(uncaughtException);
    }

    /**
     * Get the name of all printers on the system.
     *
     * @return list of printers names.
     */
    public static String[] getListPrintServicesNames() {
        PrintService[] printServices = PrinterJob.lookupPrintServices();
        String[] printServicesNames = new String[printServices.length];
        for (int i = 0; i < printServices.length; i++) {
            printServicesNames[i] = printServices[i].getName();
        }
        return printServicesNames;
    }

    /**
     * Get default system printer. This call is slow, try to use it only once
     * and reuse the PrintService variable.
     *
     * @return default printer.
     */
    public static PrintService getDefaultPrintService() {
        PrintService foundService = PrintServiceLookup.lookupDefaultPrintService();
        if (foundService == null) {
            throw new IllegalArgumentException("Default Print Service is not found");
        }
        return foundService;

    }

    /**
     * Get print having its name containing the passed string.
     * <p>
     * This call is slow, try to use it only once and reuse the PrintService
     * variable.
     *
     * @param printServiceName name of the printer to find.
     * @return found printer;
     */
    public static PrintService getPrintServiceByName(String printServiceName) {
        PrintService[] printServices = PrinterJob.lookupPrintServices();
        PrintService foundService = null;

        // Primera búsqueda: por getName() - comparación exacta
        for (PrintService service : printServices) {
            if (service.getName().compareTo(printServiceName) == 0) {
                foundService = service;
                break;
            }
        }
        if (foundService != null) {
            return foundService;
        }

        // Segunda búsqueda: por getName() - comparación case-insensitive
        for (PrintService service : printServices) {
            if (service.getName().compareToIgnoreCase(printServiceName) == 0) {
                foundService = service;
                break;
            }
        }
        if (foundService != null) {
            return foundService;
        }

        // Tercera búsqueda: por getName() - contiene el nombre
        for (PrintService service : printServices) {
            if (service.getName().toLowerCase().contains(printServiceName.toLowerCase())) {
                foundService = service;
                break;
            }
        }
        if (foundService != null) {
            return foundService;
        }

        // Cuarta búsqueda: por atributos del PrintService - comparación exacta
        for (PrintService service : printServices) {
            if (isPrinterMatchByAttributes(service, printServiceName, true)) {
                foundService = service;
                break;
            }
        }
        if (foundService != null) {
            return foundService;
        }

        // Quinta búsqueda: por atributos del PrintService - comparación case-insensitive
        for (PrintService service : printServices) {
            if (isPrinterMatchByAttributes(service, printServiceName, false)) {
                foundService = service;
                break;
            }
        }
        if (foundService != null) {
            return foundService;
        }
        
        return foundService;
    }

    /**
     * Método helper para buscar impresoras por sus atributos internos
     * @param service PrintService a verificar
     * @param printerName nombre de la impresora a buscar
     * @param exactMatch true para comparación exacta, false para case-insensitive
     * @return true si encuentra coincidencia
     */
    private static boolean isPrinterMatchByAttributes(PrintService service, String printerName, boolean exactMatch) {
        try {
            // Método 1: Buscar en toString() con diferentes formatos
            String serviceString = service.toString();
            String searchString = exactMatch ? serviceString : serviceString.toLowerCase();
            
            // Buscar patrones comunes en toString()
            String[] patterns = {
                "printer = \"" + printerName + "\"",
                "printer=\"" + printerName + "\"",
                "printer='" + printerName + "'",
                "printer=" + printerName,
                "name=\"" + printerName + "\"",
                "name='" + printerName + "'"
            };
            
            for (String pattern : patterns) {
                String searchPattern = exactMatch ? pattern : pattern.toLowerCase();
                if (searchString.contains(searchPattern)) {
                    return true;
                }
            }
            
            // Método 2: Buscar usando reflexión en campos específicos
            try {
                java.lang.reflect.Field[] fields = service.getClass().getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    field.setAccessible(true);
                    Object value = field.get(service);
                    if (value != null) {
                        String fieldValue = value.toString();
                        String compareValue = exactMatch ? fieldValue : fieldValue.toLowerCase();
                        String compareName = exactMatch ? printerName : printerName.toLowerCase();
                        
                        if (compareValue.equals(compareName) || compareValue.contains(compareName)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                // Si la reflexión falla, continuar con otros métodos
            }
            
            // Método 3: Buscar en atributos específicos conocidos
            javax.print.attribute.AttributeSet attributes = service.getAttributes();
            if (attributes != null) {
                for (javax.print.attribute.Attribute attr : attributes.toArray()) {
                    String attrString = attr.toString();
                    String compareAttr = exactMatch ? attrString : attrString.toLowerCase();
                    String compareName = exactMatch ? printerName : printerName.toLowerCase();
                    
                    if (compareAttr.equals(compareName) || compareAttr.contains(compareName)) {
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            // Si hay algún error, continuar con el siguiente servicio
        }
        
        return false;
    }

}
