package com.franco.dev.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Collections;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private JwtAuthenticationProvider authenticationProvider;
    @Autowired
    private JwtAuthenticationEntryPoint entryPoint;

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(Collections.singletonList(authenticationProvider));
    }

    @Bean
    public JwtAuthenticationTokenFilter authenticationTokenFilter() {
        JwtAuthenticationTokenFilter filter = new JwtAuthenticationTokenFilter();
        filter.setAuthenticationManager(authenticationManager());
        filter.setAuthenticationSuccessHandler(new JwtSuccessHandler());
        return filter;
    }

    /**
     * Rutas que se usan solo desde la misma maquina de la filial: la autenticacion de Google Drive.
     * El manual las usa desde {@code http://localhost:8082}.
     *
     * <p>{@code /api/backup/**} no va aca: sus metodos ya piden {@code hasRole('ADMIN')} y caen en
     * la regla general, que exige token.
     */
    static final String[] RUTAS_SOLO_LOCALES = {"/auth/google/**"};

    /**
     * Rutas que tienen que responder sin token. Cada una tiene un llamador conocido:
     * <ul>
     *   <li>{@code /login}: el login del desktop.</li>
     *   <li>{@code /public/**}: captura de cupon desde el telefono y configuracion inicial.</li>
     *   <li>{@code /api/facturas-legales/**}: el central (FacturaLegalFilialService), sin token.</li>
     *   <li>{@code /subscriptions/**}: websocket del desktop; el navegador no puede mandar el header
     *       Authorization en el handshake. Autenticarlo queda para otra issue.</li>
     *   <li>{@code /actuator/health} y {@code /api/version}: el script de auto-update. Si dejan de
     *       responder, la filial hace rollback de la version.</li>
     *   <li>{@code /error}: la pagina de error de Spring.</li>
     * </ul>
     */
    static final String[] RUTAS_PUBLICAS = {
            "/login",
            "/public/**",
            "/api/facturas-legales/**",
            "/subscriptions/**",
            "/actuator/health", "/actuator/health/**",
            "/api/version",
            "/error"
    };

    /**
     * Deny by default (issue #204): lo que no esta en las listas de arriba exige token. Antes solo se
     * cerraban rutas puntuales y {@code /auth/google/**} (que puede borrar los tokens de Google Drive)
     * quedaba abierta a cualquiera en la red. El token lo valida {@link JwtAuthenticationTokenFilter} en
     * {@code /graphql/**}; cualquier otra ruta sin regla responde 401.
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable()
                .authorizeRequests()
                .antMatchers(RUTAS_SOLO_LOCALES).access("hasIpAddress('127.0.0.1') or hasIpAddress('::1')")
                .antMatchers(RUTAS_PUBLICAS).permitAll()
                .anyRequest().authenticated()
                .and()
                .exceptionHandling().authenticationEntryPoint(entryPoint)
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .addFilterBefore(authenticationTokenFilter(), UsernamePasswordAuthenticationFilter.class)
                .headers().cacheControl();
    }
}
