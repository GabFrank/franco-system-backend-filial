package com.franco.dev.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private final JwtClaves claves;

    public JwtValidator(JwtClaves claves) {
        this.claves = claves;
    }

    /** El usuario del token, o null si ninguna clave aceptada valida la firma. Nunca lanza. */
    public JwtUser validate(String token) {
        if (token == null) return null;
        for (byte[] clave : claves.aceptadas()) {
            Claims body;
            try {
                body = Jwts.parser().setSigningKey(clave).parseClaimsJws(token).getBody();
            } catch (RuntimeException e) {
                continue;
            }
            JwtUser jwtUser = new JwtUser();

            // El generador (JwtGenerator) pone el nickname en el SUBJECT, no en un claim
            // "nickname": esta linea leia siempre null y JwtUserDetails.getUsername() quedaba
            // vacio en todo el filial. No se notaba porque nadie lo usaba del lado del servidor
            // --hasta que marcar/reabrir un cobro tuvo que saber QUIEN lo hizo sin confiar en el
            // cliente. Medido el 2026-09-21 con el contexto instrumentado. El subject viaja
            // firmado igual que cualquier claim; leerlo no cambia el modelo de confianza.
            String nickname = (String) body.get("nickname");
            if (nickname == null || nickname.trim().isEmpty()) nickname = body.getSubject();
            if (nickname == null || nickname.trim().isEmpty()) return null; // firma valida pero sin usuario
            jwtUser.setNickname(nickname);
            return jwtUser;
        }
        log.warn("Token de sesion rechazado: ninguna clave aceptada valida la firma.");
        return null;
    }
}
