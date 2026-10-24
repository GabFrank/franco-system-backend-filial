package com.franco.dev.security;

import com.franco.dev.security.jwt.JwtGenerator;
import com.franco.dev.security.model.JwtAuthenticationToken;
import com.franco.dev.service.personas.UsuarioService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Firma y validacion del token de sesion con las claves de JwtClaves. */
class JwtValidatorTest {

    /** Como la emiten los servidores viejos: el String se decodifica como base64. */
    private static final String LEGACY = "Graphql";

    private static String claveNueva() {
        byte[] b = new byte[66];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private static JwtClaves claves(String secret, String aceptar, boolean legacy) {
        return new JwtClaves(secret, aceptar, legacy);
    }

    /** Token como lo emite el JwtGenerator viejo del central: subject + nickname + password. */
    private static String tokenViejoDelCentral(String nickname) {
        Claims claims = Jwts.claims().setSubject(nickname);
        claims.put("nickname", nickname);
        claims.put("password", "x");
        return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, LEGACY).compact();
    }

    /** Token como lo emite el JwtGenerator del filial: solo subject, sin claim "nickname". */
    private static String tokenDelFilial(String nickname, String claveBase64) {
        Claims claims = Jwts.claims().setSubject(nickname);
        claims.put("password", "x");
        return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, claveBase64).compact();
    }

    // --- nickname (comportamiento previo, con las claves por defecto) ---

    @Test
    void leeElClaimNicknameCuandoEstaPresente() {
        JwtValidator v = new JwtValidator(claves(LEGACY, "", true));
        assertEquals("MR", v.validate(tokenViejoDelCentral("MR")).getNickname());
    }

    @Test
    void caeAlSubjectCuandoNoHayClaimNickname() {
        // El JwtGenerator del filial nunca escribe el claim "nickname". Sin este
        // fallback el principal quedaba null y los servicios que resuelven al
        // usuario por SecurityContext reportaban falta de permisos.
        JwtValidator v = new JwtValidator(claves(LEGACY, "", true));
        assertEquals("mr", v.validate(tokenDelFilial("mr", LEGACY)).getNickname());
    }

    @Test
    void caeAlSubjectCuandoElClaimNicknameEstaVacio() {
        Claims claims = Jwts.claims().setSubject("MR");
        claims.put("nickname", "   ");
        String token = Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, LEGACY).compact();
        assertEquals("MR", new JwtValidator(claves(LEGACY, "", true)).validate(token).getNickname());
    }

    @Test
    void nicknameNuloSiElTokenNoTraeNiClaimNiSubject() {
        Claims claims = Jwts.claims();
        claims.put("password", "x");
        String token = Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, LEGACY).compact();
        assertNull(new JwtValidator(claves(LEGACY, "", true)).validate(token).getNickname());
    }

    // --- rotacion de clave ---

    @Test
    void pasada1_aceptaLaClaveNuevaYSigueFirmandoConLaHistorica() {
        String nueva = claveNueva();
        JwtClaves c = claves(LEGACY, nueva, true);
        JwtValidator v = new JwtValidator(c);

        assertEquals("mr", v.validate(tokenDelFilial("mr", nueva)).getNickname());
        assertEquals("MR", v.validate(tokenViejoDelCentral("MR")).getNickname());
        String emitido = new JwtGenerator(mock(UsuarioService.class), c).generate(usuario("MR"));
        // un servidor viejo (solo conoce la historica) lo sigue aceptando
        assertNotNull(new JwtValidator(claves(LEGACY, "", true)).validate(emitido));
    }

    @Test
    void pasada2_firmaConLaNuevaYSigueAceptandoLosTokensViejos() {
        String nueva = claveNueva();
        JwtClaves c = claves(nueva, nueva, true);
        JwtValidator v = new JwtValidator(c);

        String emitido = new JwtGenerator(mock(UsuarioService.class), c).generate(usuario("MR"));
        assertEquals("MR", v.validate(emitido).getNickname());
        assertEquals("MR", v.validate(tokenViejoDelCentral("MR")).getNickname());
        // un servidor en la pasada 1 acepta lo que firma uno en la pasada 2
        assertNotNull(new JwtValidator(claves(LEGACY, nueva, true)).validate(emitido));
        // uno sin la pasada 1, no
        assertNull(new JwtValidator(claves(LEGACY, "", true)).validate(emitido));
    }

    @Test
    void sinLegacyLaClaveHistoricaYaNoEntra() {
        String nueva = claveNueva();
        JwtValidator v = new JwtValidator(claves(nueva, "", false));
        assertNull(v.validate(tokenViejoDelCentral("MR")));
    }

    @Test
    void volverAtrasPorConfiguracionSigueAceptandoLosTokensNuevos() {
        String nueva = claveNueva();
        String firmadoConLaNueva = tokenDelFilial("MR", nueva);
        JwtClaves atras = claves(LEGACY, nueva, true);
        assertNotNull(new JwtValidator(atras).validate(firmadoConLaNueva));
        assertArrayEquals(io.jsonwebtoken.impl.TextCodec.BASE64.decode(LEGACY), atras.firma());
    }

    @Test
    void otraClaveOTokenRotoDanNullSinLanzar() {
        JwtValidator v = new JwtValidator(claves(LEGACY, "", true));
        assertNull(v.validate(tokenDelFilial("MR", claveNueva())));
        assertNull(v.validate("no-es-un-token"));
        assertNull(v.validate(null));
    }

    @Test
    void elTokenNuevoNoLlevaLaClaveDelUsuario() {
        JwtClaves c = claves(claveNueva(), "", true);
        JwtUser u = usuario("MR");
        u.setPassword("SECRETA");
        String token = new JwtGenerator(mock(UsuarioService.class), c).generate(u);
        Claims body = Jwts.parser().setSigningKey(c.firma()).parseClaimsJws(token).getBody();
        assertFalse(body.containsKey("password"));
        assertEquals("MR", body.getSubject());
    }

    // --- provider: token invalido es 401 (AuthenticationException), no un NPE ---

    @Test
    void elProviderRechazaConBadCredentialsYAceptaUnTokenValido() {
        JwtClaves c = claves(LEGACY, "", true);
        JwtAuthenticationProvider p = new JwtAuthenticationProvider(mock(UsuarioService.class));
        ReflectionTestUtils.setField(p, "validator", new JwtValidator(c));

        Authentication ok = p.authenticate(new JwtAuthenticationToken(tokenViejoDelCentral("MR")));
        assertEquals("MR", ok.getName());
        assertThrows(BadCredentialsException.class,
                () -> p.authenticate(new JwtAuthenticationToken(tokenDelFilial("MR", claveNueva()))));
    }

    private static JwtUser usuario(String nickname) {
        JwtUser u = new JwtUser();
        u.setNickname(nickname);
        return u;
    }
}
