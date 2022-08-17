package com.franco.dev.utilitarios;

import java.time.LocalDateTime;
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
    public static String dateToStringShort(LocalDateTime d) {
        return formatter2.format(d);
    }

    public static LocalDateTime toDate(String s) {
        return LocalDateTime.parse(s, formatter);
    }
    public static LocalDateTime stringToDate(String s) {
        return LocalDateTime.parse(s, formatter);
    }

}
