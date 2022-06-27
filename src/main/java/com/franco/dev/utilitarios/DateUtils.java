package com.franco.dev.utilitarios;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class DateUtils {

    private static final String PATTERN = "yyyy-MM-dd HH:mm";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN);

    public static String toString(LocalDateTime d) {
        return formatter.format(d);
    }

    public static LocalDateTime toDate(String s) {
            return LocalDateTime.parse(s, formatter);
        }

}
