package com.franco.dev.security;

import com.franco.dev.domain.personas.Usuario;
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

            jwtUser.setNickname((String) body.get("nickname"));
            jwtUser.setPassword((String) body.get("password"));
        }
        catch (Exception e) {
            System.out.println(e);
        }

        return jwtUser;
    }
}
