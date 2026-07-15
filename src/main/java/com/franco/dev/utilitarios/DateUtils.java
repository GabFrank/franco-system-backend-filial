package com.franco.dev.utilitarios;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public class DateUtils {

    private static final String PATTERN = "yyyy-MM-dd HH:mm";
    private static final String PATTERN_ISO = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String PATTERN_ONLY_DATE = "yyyy-MM-dd";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN);
    private static final DateTimeFormatter formatter_iso = DateTimeFormatter.ofPattern(PATTERN_ISO);
    private static final DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern(PATTERN_ONLY_DATE);

    public static String toString(LocalDateTime d) {
        return formatter.format(d);
    }
    
    public static String dateToString(LocalDateTime d) {
        return formatter.format(d);
    }

    /**
     * Convierte una fecha a un string en formato yyyy-MM-dd
     * @param d fecha a convertir
     * @return string en formato yyyy-MM-dd
     */
    public static String dateToStringShort(LocalDateTime d) {
        return formatter2.format(d);
    }
    
    public static String toStringOnlyDate(LocalDateTime d) {
        return formatter2.format(d);
    }

    public static LocalDateTime toDate(String s) {
        return LocalDateTime.parse(s, formatter);
    }
    
    public static LocalDateTime stringToDate(String s) {
        if(s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        if (s.contains("T")) {
            // ISO-8601 con zona/offset (ej. "2025-10-13T18:06:28.768Z" o "...-03:00"),
            // milisegundos opcionales. Se convierte al huso horario del servidor.
            try {
                return OffsetDateTime.parse(s)
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (java.time.format.DateTimeParseException ignored) {
                // ISO local sin zona, con fracciones de segundo opcionales (ej. "2026-06-13T16:39:04.456")
                return LocalDateTime.parse(s);
            }
        }
        // Handle date only format (yyyy-MM-dd) - add default time
        if(s.length() == 10) {
            return LocalDateTime.parse(s + " 00:00", formatter);
        }
        // Fallback to original formatter for backward compatibility
        return LocalDateTime.parse(s, formatter);
    }
    
    public static LocalDateTime getFirstDayOfMonth(long offsetMonth) {
        LocalDateTime hoy = LocalDateTime.now().withHour(00).withMinute(00).withSecond(00).plusMonths(offsetMonth);
        return hoy.with(TemporalAdjusters.firstDayOfMonth());
    }

    public static LocalDateTime getLastDayOfMonth(long offsetMonth) {
        LocalDateTime hoy = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).plusMonths(offsetMonth);
        return hoy.with(TemporalAdjusters.lastDayOfMonth());
    }

    public static String dateToStringWithFormat(LocalDateTime date, String format){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return formatter.format(date);
    }

}