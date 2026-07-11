package com.franco.dev.service.impresion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dispositivo de impresion detectado por CUPS (lpinfo -v) que todavia puede no tener
 * una cola creada. Se usa para "detectar para instalar".
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DispositivoDetectado {
    /** Clase reportada por lpinfo: direct | network | serial | file. */
    private String clase;
    /** URI del dispositivo, ej: usb://EPSON/L575%20Series?serial=... o socket://192.168.0.50 */
    private String uri;
    /** Nombre legible derivado de la URI. */
    private String nombre;
    /** Descripcion (la URI completa). */
    private String descripcion;
}
