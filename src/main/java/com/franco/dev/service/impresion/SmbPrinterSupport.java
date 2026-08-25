package com.franco.dev.service.impresion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers puros para impresoras compartidas por un host Windows (SMB/CIFS).
 * Sin estado ni I/O: se testean directamente (ver SmbPrinterSupportTest).
 *
 * El transporte real lo hace CUPS: se instala una cola con device-uri {@code smb://...} y el
 * backend {@code /usr/lib/cups/backend/smb} (smbspool, de Samba) entrega el job al share de
 * Windows. Para el motor de impresion Java es una cola CUPS mas.
 */
public final class SmbPrinterSupport {

    /** Clase que se reporta al frontend para distinguirlos de los dispositivos de lpinfo. */
    public static final String CLASE = "smb";

    private static final String MASCARA = "***";

    private SmbPrinterSupport() {
    }

    /**
     * Arma el device-uri que entiende smbspool:
     * {@code smb://[usuario[:password]@][dominio/]host/recurso}.
     *
     * <p>La contrasena queda SOLO aca: la URI se pasa a {@code lpadmin} y CUPS la guarda en
     * {@code /etc/cups/printers.conf} (root-only). Nunca se persiste en la BD, que se replica
     * a todas las filiales.</p>
     */
    public static String deviceUri(String host, String recurso, String usuario,
                                   String dominio, String password) {
        if (esVacio(host)) {
            throw new IllegalArgumentException("Host SMB requerido");
        }
        if (esVacio(recurso)) {
            throw new IllegalArgumentException("Recurso (share) SMB requerido");
        }
        StringBuilder uri = new StringBuilder("smb://");
        if (!esVacio(usuario)) {
            uri.append(codificar(usuario));
            if (!esVacio(password)) {
                uri.append(':').append(codificar(password));
            }
            uri.append('@');
        }
        if (!esVacio(dominio)) {
            uri.append(codificar(dominio)).append('/');
        }
        uri.append(codificar(host)).append('/').append(codificar(recurso));
        return uri.toString();
    }

    /**
     * Parsea la salida de {@code smbclient -L //host -g} y devuelve solo los shares de impresora.
     * Formato por linea: {@code Tipo|Nombre|Comentario} (Disk, Printer, IPC, Server, Workgroup).
     *
     * <p>Las URIs devueltas van SIN credenciales: esta lista viaja al frontend.</p>
     */
    public static List<DispositivoDetectado> recursosDeImpresora(String host, String salida) {
        List<DispositivoDetectado> recursos = new ArrayList<>();
        if (salida == null || salida.isEmpty()) {
            return recursos;
        }
        for (String linea : salida.split("\\R")) {
            String[] partes = linea.split("\\|", 3);
            if (partes.length < 2 || !"Printer".equalsIgnoreCase(partes[0].trim())) {
                continue;
            }
            String nombre = partes[1].trim();
            if (nombre.isEmpty()) {
                continue;
            }
            DispositivoDetectado d = new DispositivoDetectado();
            d.setClase(CLASE);
            d.setUri(deviceUri(host, nombre, null, null, null));
            d.setNombre(nombre);
            d.setDescripcion(partes.length > 2 ? partes[2].trim() : "");
            recursos.add(d);
        }
        return recursos;
    }

    /** Enmascara la password del userinfo de cualquier URI smb:// que aparezca en un texto. */
    public static String redactarTexto(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.replaceAll("(smb://[^:/@\\s]+):[^@\\s]*@", "$1:" + MASCARA + "@");
    }

    /**
     * Enmascara la password en los argumentos antes de loguear el comando: el userinfo de una
     * URI {@code smb://usuario:password@...} y el {@code -U usuario%password} de smbclient.
     */
    public static List<String> redactar(List<String> comando) {
        List<String> redactado = new ArrayList<>(comando.size());
        String anterior = "";
        for (String arg : comando) {
            if (("-U".equals(anterior) || "--user".equals(anterior)) && arg.contains("%")) {
                redactado.add(arg.substring(0, arg.indexOf('%') + 1) + MASCARA);
            } else {
                redactado.add(redactarTexto(arg));
            }
            anterior = arg;
        }
        return redactado;
    }

    private static boolean esVacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Percent-encoding RFC 3986: deja pasar los "unreserved" y codifica todo lo demas.
     * No sirve {@code URLEncoder}, que codifica el espacio como {@code +} (valido en un query
     * string, no en el userinfo ni en el path de una URI).
     */
    private static String codificar(String valor) {
        StringBuilder salida = new StringBuilder(valor.length());
        for (byte b : valor.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~';
            if (unreserved) {
                salida.append((char) c);
            } else {
                salida.append('%').append(String.format("%02X", c));
            }
        }
        return salida.toString();
    }
}
