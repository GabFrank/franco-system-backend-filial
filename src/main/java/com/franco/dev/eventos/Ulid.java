package com.franco.dev.eventos;

import java.security.SecureRandom;

/** ULID (26 caracteres Crockford base32): ordenable por tiempo y unico sin coordinar nodos. */
public final class Ulid {

    private static final char[] ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    public static String nuevo() {
        return nuevo(System.currentTimeMillis());
    }

    static String nuevo(long epochMs) {
        char[] out = new char[26];
        long t = epochMs;
        for (int i = 9; i >= 0; i--) {
            out[i] = ALFABETO[(int) (t & 31)];
            t >>>= 5;
        }
        byte[] r = new byte[10];
        RANDOM.nextBytes(r);
        // 80 bits aleatorios -> 16 caracteres de 5 bits
        long hi = 0;
        for (int i = 0; i < 5; i++) hi = (hi << 8) | (r[i] & 0xff);
        long lo = 0;
        for (int i = 5; i < 10; i++) lo = (lo << 8) | (r[i] & 0xff);
        for (int i = 17; i >= 10; i--) {
            out[i] = ALFABETO[(int) (hi & 31)];
            hi >>>= 5;
        }
        for (int i = 25; i >= 18; i--) {
            out[i] = ALFABETO[(int) (lo & 31)];
            lo >>>= 5;
        }
        return new String(out);
    }
}
