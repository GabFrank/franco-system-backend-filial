package com.franco.dev.security;

import io.jsonwebtoken.impl.TextCodec;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Formato de las claves configuradas, alias de la historica y huella. */
class JwtClavesTest {

    private static final String LEGACY = "Graphql";
    private static final String NUEVA = Base64.getEncoder().encodeToString(new byte[66]).replace('A', 'Q');

    @Test
    void sinConfiguracionFirmaYAceptaSoloLaHistorica() {
        JwtClaves c = new JwtClaves(LEGACY, "", true);
        assertArrayEquals(TextCodec.BASE64.decode(LEGACY), c.firma());
        assertEquals(1, c.aceptadas().size());
    }

    @Test
    void lasVariantesDeLaHistoricaSonLaMismaClave() {
        // el decoder de jjwt ignora caracteres sueltos: todas firman igual que "Graphql"
        for (String alias : new String[]{"Graphqm", "Graph ql", "Graphql\n"}) {
            JwtClaves c = new JwtClaves(alias, "", false);
            assertArrayEquals(TextCodec.BASE64.decode(LEGACY), c.firma(), alias);
        }
    }

    @Test
    void claveCortaOConCaracteresInvalidosFrenaElArranque() {
        assertThrows(IllegalStateException.class, () -> new JwtClaves("QUJDRA==", "", true)); // 4 bytes
        String urlSafe = NUEVA.replace('+', '-');
        assertThrows(IllegalStateException.class, () -> new JwtClaves(urlSafe.replace('Q', '_'), "", true));
        assertThrows(IllegalStateException.class, () -> new JwtClaves(LEGACY, NUEVA.substring(1), true));
    }

    @Test
    void noRepiteClavesNiAgregaVacias() {
        JwtClaves c = new JwtClaves(NUEVA, NUEVA, true);
        assertEquals(2, c.aceptadas().size()); // la nueva y la historica
        assertEquals(1, new JwtClaves(NUEVA, "  ", false).aceptadas().size());
    }

    @Test
    void laHuellaSaleDeLosBytesYNoMuestraLaClave() {
        String h = JwtClaves.huella(TextCodec.BASE64.decode("Graphqm"));
        assertEquals(JwtClaves.huella(TextCodec.BASE64.decode(LEGACY)), h);
        assertEquals(8, h.length());
        assertFalse(NUEVA.contains(JwtClaves.huella(Base64.getDecoder().decode(NUEVA))));
    }
}
