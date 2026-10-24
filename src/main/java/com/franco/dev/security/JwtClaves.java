package com.franco.dev.security;

import io.jsonwebtoken.impl.TextCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Claves del token de sesion, leidas de la configuracion de cada instalacion.
 *
 * <ul>
 *   <li>{@code app.jwt.secret} (APP_JWT_SECRET): firma y valida.</li>
 *   <li>{@code app.jwt.aceptar} (APP_JWT_ACEPTAR): solo valida; sirve para pasar a una clave nueva sin
 *       cortar los tokens que firman los demas servidores de la red.</li>
 *   <li>{@code app.jwt.legacy} (APP_JWT_LEGACY): ademas acepta la clave historica.</li>
 * </ul>
 *
 * Una clave configurada tiene que ser base64 estandar de al menos 32 bytes: si no, el arranque falla.
 * La clave historica se reconoce por sus bytes (el decoder de jjwt ignora caracteres sueltos).
 */
@Component
@Lazy(false)
public class JwtClaves {

    private static final Logger log = LoggerFactory.getLogger(JwtClaves.class);

    static final String LEGACY = "Graphql";
    static final int MINIMO_BYTES = 32;
    private static final Pattern BASE64 = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");
    private static final byte[] LEGACY_BYTES = TextCodec.BASE64.decode(LEGACY);

    private final byte[] firma;
    private final List<byte[]> aceptadas;

    public JwtClaves(@Value("${app.jwt.secret:" + LEGACY + "}") String secret,
                     @Value("${app.jwt.aceptar:}") String aceptar,
                     @Value("${app.jwt.legacy:true}") boolean legacy) {
        this.firma = esLegacy(secret) ? LEGACY_BYTES : decodificar("app.jwt.secret", secret);
        List<byte[]> lista = new ArrayList<>();
        agregar(lista, firma);
        if (aceptar != null && !aceptar.trim().isEmpty()) {
            agregar(lista, esLegacy(aceptar) ? LEGACY_BYTES : decodificar("app.jwt.aceptar", aceptar));
        }
        if (legacy) agregar(lista, LEGACY_BYTES);
        this.aceptadas = Collections.unmodifiableList(lista);

        List<String> huellas = new ArrayList<>();
        for (byte[] k : aceptadas) huellas.add(huella(k));
        log.info("Token de sesion: firma con la clave {}; acepta {}", huella(firma), huellas);
        if (Arrays.equals(firma, LEGACY_BYTES)) {
            log.warn("Token de sesion: firma con la clave historica. Configurar APP_JWT_SECRET.");
        }
    }

    public byte[] firma() {
        return firma.clone();
    }

    public List<byte[]> aceptadas() {
        return aceptadas;
    }

    /** Primeros 8 hex del SHA-256 de los bytes de la clave: identifica la clave sin mostrarla. */
    public static String huella(byte[] clave) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(clave);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean esLegacy(String valor) {
        if (valor == null) return false;
        try {
            return Arrays.equals(TextCodec.BASE64.decode(valor), LEGACY_BYTES);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] decodificar(String propiedad, String valor) {
        String v = valor == null ? "" : valor.trim();
        if (!BASE64.matcher(v).matches() || v.length() % 4 != 0) {
            throw new IllegalStateException(propiedad + ": la clave no es base64 estandar (A-Z a-z 0-9 + /).");
        }
        byte[] bytes = Base64.getDecoder().decode(v.getBytes(StandardCharsets.US_ASCII));
        if (bytes.length < MINIMO_BYTES) {
            throw new IllegalStateException(propiedad + ": la clave tiene " + bytes.length
                    + " bytes; el minimo es " + MINIMO_BYTES + ".");
        }
        return bytes;
    }

    private static void agregar(List<byte[]> lista, byte[] clave) {
        for (byte[] k : lista) if (Arrays.equals(k, clave)) return;
        lista.add(clave);
    }
}
