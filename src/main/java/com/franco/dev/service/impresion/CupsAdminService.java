package com.franco.dev.service.impresion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Administracion de impresoras a nivel de CUPS (Linux) via los comandos {@code lpinfo}
 * y {@code lpadmin}. Permite:
 * <ul>
 *   <li>Detectar dispositivos conectables (USB/red/serial) aunque no tengan cola creada.</li>
 *   <li>Instalar una cola nueva (RAW para termicas ESC/POS, o driverless para normales).</li>
 * </ul>
 *
 * REQUISITO OPERATIVO: el proceso del backend debe tener permisos de administracion de
 * CUPS. {@code lpinfo} suele requerir root; {@code lpadmin} requiere pertenecer al grupo
 * de administracion de CUPS (SystemGroup en cupsd.conf). Si el backend corre como
 * servicio systemd, agregar su usuario a ese grupo (o ejecutarlo con privilegios).
 */
@Service
public class CupsAdminService {

    private static final Logger log = LoggerFactory.getLogger(CupsAdminService.class);
    private static final long TIMEOUT_SEG = 20;

    /** Detecta dispositivos conectables via {@code lpinfo -v} (con fallback a {@code sudo -n}). */
    public List<DispositivoDetectado> detectarDispositivos() {
        List<DispositivoDetectado> lista = new ArrayList<>();
        Resultado r = ejecutarConFallbackSudo(Arrays.asList("lpinfo", "-v"));
        for (String linea : r.salida.split("\\R")) {
            DispositivoDetectado d = parsearLinea(linea);
            if (d != null) {
                lista.add(d);
            }
        }
        return lista;
    }

    /**
     * Instala una cola CUPS nueva.
     * @param nombreCola nombre deseado (se sanea a [A-Za-z0-9_-]).
     * @param uri URI del dispositivo (de {@link #detectarDispositivos()}).
     * @param raw true = cola RAW (termicas ESC/POS); false = driverless (everywhere).
     * @return true si {@code lpadmin} termino con exito.
     */
    public boolean instalarImpresora(String nombreCola, String uri, boolean raw) {
        String cola = sanearNombre(nombreCola);
        if (cola.isEmpty() || uri == null || uri.isBlank()) {
            log.warn("[CUPS] Parametros invalidos para instalar (cola='{}', uri='{}')", cola, uri);
            return false;
        }
        // printer-error-policy=retry-current-job: ante un error del backend la cola NO se pausa
        // (evita el "stop-printer" por defecto, que deja la impresora inactiva y los jobs pendientes).
        List<String> cmd = new ArrayList<>(Arrays.asList(
                "lpadmin", "-p", cola, "-E", "-v", uri, "-m", raw ? "raw" : "everywhere",
                "-o", "printer-error-policy=retry-current-job"));
        return ejecutar(cmd);
    }

    // ---- helpers ----

    private DispositivoDetectado parsearLinea(String lineaRaw) {
        if (lineaRaw == null) {
            return null;
        }
        String linea = lineaRaw.trim();
        if (linea.isEmpty()) {
            return null;
        }
        int sp = linea.indexOf(' ');
        if (sp < 0) {
            return null;
        }
        String clase = linea.substring(0, sp).trim();
        String uri = linea.substring(sp + 1).trim();
        // Ignoramos entradas sin dispositivo concreto (esquemas base).
        if (uri.isEmpty() || !uri.contains("://") && !uri.contains(":")) {
            return null;
        }
        DispositivoDetectado d = new DispositivoDetectado();
        d.setClase(clase);
        d.setUri(uri);
        d.setNombre(nombreDesdeUri(uri));
        d.setDescripcion(uri);
        return d;
    }

    private String nombreDesdeUri(String uri) {
        try {
            if (uri.startsWith("usb://")) {
                String resto = uri.substring("usb://".length());
                int q = resto.indexOf('?');
                if (q >= 0) {
                    resto = resto.substring(0, q);
                }
                resto = resto.replace('/', ' ').trim();
                return URLDecoder.decode(resto, StandardCharsets.UTF_8.name());
            }
            if (uri.startsWith("socket://") || uri.startsWith("ipp://")
                    || uri.startsWith("ipps://") || uri.startsWith("lpd://")) {
                return "Red " + uri.replaceAll("^\\w+://", "");
            }
            if (uri.startsWith("serial:")) {
                return "Serial " + uri.substring("serial:".length());
            }
        } catch (Exception e) {
            // fallback abajo
        }
        return uri;
    }

    private boolean ejecutar(List<String> cmd) {
        return ejecutarConFallbackSudo(cmd).codigo == 0;
    }

    /**
     * Ejecuta un comando; si falla por permisos (tipicamente {@code lpadmin: Forbidden} porque el
     * proceso corre headless sin permisos de CUPS, ej. servicio systemd 'deploy' en Fedora),
     * reintenta con {@code sudo -n} para correrlo como root. Requiere una entrada sudoers NOPASSWD
     * para el usuario del servicio sobre lpadmin/lpinfo. Si {@code sudo -n} no esta permitido,
     * devuelve el resultado del intento directo (con el error real).
     */
    private Resultado ejecutarConFallbackSudo(List<String> cmd) {
        Resultado r = correr(cmd);
        if (r.codigo != 0 && requiereSudo(r.salida)) {
            List<String> conSudo = new ArrayList<>();
            conSudo.add("sudo");
            conSudo.add("-n");
            conSudo.addAll(cmd);
            // Devolvemos el intento con sudo: si funcionó, código 0; si no, trae el error real de sudo.
            return correr(conSudo);
        }
        return r;
    }

    /** Heuristica: la salida sugiere que fallo por falta de permisos y conviene reintentar con sudo. */
    private boolean requiereSudo(String salida) {
        if (salida == null || salida.isBlank()) {
            return true;
        }
        String s = salida.toLowerCase();
        return s.contains("forbidden") || s.contains("not authorized")
                || s.contains("no autorizado") || s.contains("permission")
                || s.contains("permiso") || s.contains("unauthorized");
    }

    private Resultado correr(List<String> cmd) {
        try {
            Process proceso = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder salida = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    salida.append(linea).append('\n');
                }
            }
            boolean termino = proceso.waitFor(TIMEOUT_SEG, TimeUnit.SECONDS);
            int codigo = termino ? proceso.exitValue() : -1;
            if (codigo != 0) {
                log.warn("[CUPS] Comando {} terminó con código {}: {}", cmd, codigo, salida.toString().trim());
            } else {
                log.info("[CUPS] Comando {} OK", cmd);
            }
            return new Resultado(codigo, salida.toString());
        } catch (Exception e) {
            log.error("[CUPS] Error ejecutando {}: {}", cmd, e.getMessage(), e);
            return new Resultado(-1, e.getMessage() == null ? "" : e.getMessage());
        }
    }

    /** Resultado de ejecutar un comando: codigo de salida + salida combinada (stdout+stderr). */
    private static final class Resultado {
        final int codigo;
        final String salida;
        Resultado(int codigo, String salida) {
            this.codigo = codigo;
            this.salida = salida;
        }
    }

    private String sanearNombre(String nombre) {
        if (nombre == null) {
            return "";
        }
        return nombre.trim().replaceAll("\\s+", "_").replaceAll("[^A-Za-z0-9_-]", "");
    }
}
