package com.franco.dev.service.utils.biometric;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RealTimeLogDecoder {
    public static LogData decodeRecordRealTimeLog52(byte[] recordData) {
        // Assuming the TCP header is a fixed size, adjust the 8 value as needed
        byte[] payload = removeTcpHeader(recordData, 8);

        // Extract User ID
        String userId = extractUserId(payload, 0, 9);

        // Parse Attendance Time
        String attTime = parseHexToTime(payload, 26, 6);

        return new LogData(userId, attTime);
    }

    private static String extractUserId(byte[] data, int start, int length) {
        byte[] userIdBytes = new byte[length];
        System.arraycopy(data, start, userIdBytes, 0, length);
        String userId = new String(userIdBytes, StandardCharsets.US_ASCII).trim();

        // Remove non-printable characters
        userId = userId.replaceAll("[^\\x20-\\x7E]", "");

        return userId;
    }

    private static byte[] removeTcpHeader(byte[] data, int headerSize) {
        byte[] payload = new byte[data.length - headerSize];
        System.arraycopy(data, headerSize, payload, 0, payload.length);
        return payload;
    }

    private static String parseHexToTime(byte[] data, int start, int length) {
        // Assuming the time is in a hex format that needs to be converted to a Date
        // Adjust the parsing logic based on the actual format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // Dummy conversion, replace with actual logic
        Date date = new Date(); // Placeholder for actual date parsing logic
        return dateFormat.format(date);
    }

    public static class LogData {
        public String userId;
        public String attTime;

        public LogData(String userId, String attTime) {
            this.userId = userId;
            this.attTime = attTime;
        }
    }
}
