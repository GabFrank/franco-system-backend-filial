package com.franco.dev.security;

import com.franco.dev.security.model.JwtAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtAuthenticationTokenFilter extends AbstractAuthenticationProcessingFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);

    public JwtAuthenticationTokenFilter() {
        super("/graphql/**");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws AuthenticationException, IOException, ServletException {

        String header = httpServletRequest.getHeader("Authorization");

        String requestURI = httpServletRequest.getRequestURI();
        if (requestURI.startsWith("/public")) {
            // Allow the request to proceed without authentication
            return null;
        }

        if (header == null || !header.startsWith("Token ")) {
            log.warn("JWT Token is missing");
            // No devolver null: AbstractAuthenticationProcessingFilter interpreta el null
            // como "autenticacion aun sin terminar", corta el doFilter y NO escribe nada
            // en la respuesta. El cliente recibe un HTTP 200 con cuerpo vacio, que Apollo
            // no puede parsear. Lanzando la excepcion se responde el 401 que corresponde.
            throw new InsufficientAuthenticationException("Falta el token de autenticacion");
        }

        String authenticationToken = header.substring(6);

        JwtAuthenticationToken token = new JwtAuthenticationToken(authenticationToken);
        return getAuthenticationManager().authenticate(token);
    }


    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);
        chain.doFilter(request, response);
    }
}
