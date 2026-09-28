package com.franco.dev.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

@Component
public class JwtValidator {


    private String secret = "Graphql";

    public JwtUser validate(String token) {

        JwtUser jwtUser = null;
        try {
            Claims body = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();

            jwtUser = new JwtUser();

            // El generador (JwtGenerator) pone el nickname en el SUBJECT, no en un claim
            // "nickname": esta linea leia siempre null y JwtUserDetails.getUsername() quedaba
            // vacio en todo el filial. No se notaba porque nadie lo usaba del lado del servidor
            // --hasta que marcar/reabrir un cobro tuvo que saber QUIEN lo hizo sin confiar en el
            // cliente. Medido el 2026-09-21 con el contexto instrumentado. El subject viaja
            // firmado igual que cualquier claim; leerlo no cambia el modelo de confianza.
            String nickname = (String) body.get("nickname");
            if (nickname == null || nickname.trim().isEmpty()) nickname = body.getSubject();
            jwtUser.setNickname(nickname);
            jwtUser.setPassword((String) body.get("password"));
        } catch (Exception e) {
            System.out.println(e);
        }

        return jwtUser;
    }
}
