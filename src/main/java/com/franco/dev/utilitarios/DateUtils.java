package com.franco.dev.utilitarios;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateUtils {

    private static final String PATTERN = "yyyy-MM-dd HH:mm";
    private static final String PATTERN2 = "yyyy-MM-dd";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN);
    private static final DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern(PATTERN2);

    public static String toString(LocalDateTime d) {
        return formatter.format(d);
    }
    public static String dateToString(LocalDateTime d) {
        return formatter.format(d);
    }

    // agregar comentario para entender el funcionamiento de la funcion
    /**
     * Convierte una fecha a un string en formato yyyy-MM-dd
     * @param d fecha a convertir
     * @return string en formato yyyy-MM-dd
     */
    public static String dateToStringShort(LocalDateTime d) {
        return formatter2.format(d);
    }

    public static LocalDateTime toDate(String s) {
        return LocalDateTime.parse(s, formatter);
    }
    
    public static LocalDateTime stringToDate(String s) {
        // Handle ISO-8601 format with timezone (e.g., "2025-10-13T18:06:28.768Z")
        if (s.contains("T") && (s.endsWith("Z") || s.contains("+"))) {
            // Parse as Instant and convert to LocalDateTime in system default timezone
            Instant instant = Instant.parse(s);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        // Fallback to original formatter for backward compatibility
        return LocalDateTime.parse(s, formatter);
    }

}