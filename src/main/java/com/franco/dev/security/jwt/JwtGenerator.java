package com.franco.dev.security.jwt;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.security.JwtClaves;
import com.franco.dev.security.JwtUser;
import com.franco.dev.service.personas.UsuarioService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtGenerator {

    @Autowired
    private final UsuarioService usuarioService;

    private final JwtClaves claves;

    public JwtGenerator(UsuarioService usuarioService, JwtClaves claves) {
        this.usuarioService = usuarioService;
        this.claves = claves;
    }


    public String generate(JwtUser jwtUser) {


        Claims claims = Jwts.claims()
                .setSubject(jwtUser.getNickname());
        claims.put("roles", jwtUser.getRoles());


        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, claves.firma())
                .compact();
    }
}
