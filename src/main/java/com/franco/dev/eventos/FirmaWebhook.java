package com.franco.dev.eventos;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Firma Standard Webhooks (standardwebhooks.com), version asimetrica "v1a" con Ed25519.
 * Contenido firmado: "{webhook-id}.{webhook-timestamp}.{cuerpo}". Cabecera: "v1a,{firma base64}".
 * La filial corre en Java 11, que no trae Ed25519: se usa BouncyCastle (ya esta en el pom).
 */
public final class FirmaWebhook {

    private final Ed25519PrivateKeyParameters privada;

    public FirmaWebhook(String semillaBase64) {
        byte[] semilla = Base64.getDecoder().decode(semillaBase64.trim());
        if (semilla.length != 32) {
            throw new IllegalArgumentException("La clave privada Ed25519 tiene que ser una semilla de 32 bytes");
        }
        this.privada = new Ed25519PrivateKeyParameters(semilla, 0);
    }

    public String clavePublicaBase64() {
        return Base64.getEncoder().encodeToString(privada.generatePublicKey().getEncoded());
    }

    public String firmar(String webhookId, long timestamp, String cuerpo) {
        byte[] msg = contenido(webhookId, timestamp, cuerpo);
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, privada);
        s.update(msg, 0, msg.length);
        return "v1a," + Base64.getEncoder().encodeToString(s.generateSignature());
    }

    /** Lo que hace el receptor: sirve para los tests y para documentar la verificacion. */
    public static boolean verificar(String publicaBase64, String webhookId, long timestamp, String cuerpo, String cabecera) {
        byte[] pub = Base64.getDecoder().decode(publicaBase64);
        byte[] msg = contenido(webhookId, timestamp, cuerpo);
        for (String parte : cabecera.split(" ")) {
            if (!parte.startsWith("v1a,")) continue;
            Ed25519Signer s = new Ed25519Signer();
            s.init(false, new Ed25519PublicKeyParameters(pub, 0));
            s.update(msg, 0, msg.length);
            if (s.verifySignature(Base64.getDecoder().decode(parte.substring(4)))) return true;
        }
        return false;
    }

    /**
     * Firma simetrica "v1" (HMAC-SHA256) de Standard Webhooks, opcional por suscripcion: los SDK oficiales
     * (standardwebhooks 1.1.x de Python y npm) solo verifican v1 e ignoran v1a. Secreto "whsec_<base64>".
     */
    public static String firmarHmac(String secreto, String webhookId, long timestamp, String cuerpo) {
        String s = secreto.trim();
        if (s.startsWith("whsec_")) s = s.substring(6);
        byte[] clave = Base64.getDecoder().decode(s);
        if (clave.length == 0) throw new IllegalArgumentException("Secreto HMAC vacio");
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(clave, "HmacSHA256"));
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(contenido(webhookId, timestamp, cuerpo)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] contenido(String webhookId, long timestamp, String cuerpo) {
        return (webhookId + "." + timestamp + "." + cuerpo).getBytes(StandardCharsets.UTF_8);
    }
}
