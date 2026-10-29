package com.franco.dev.security;

import com.franco.dev.security.model.JwtAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.Filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Que ruta de la filial responde sin token, cual solo desde la misma maquina y cual exige token
 * (issue #204). Levanta solo SecurityConfig y un controller de prueba, sin base de datos.
 */
@SpringJUnitWebConfig(classes = SecurityConfigTest.Config.class, initializers = SecurityConfigTest.ProviderDePrueba.class)
class SecurityConfigTest {

    private static final String TOKEN_BUENO = "Token bueno";
    private static final String IP_DE_LA_RED = "192.168.0.50";

    @Autowired
    private WebApplicationContext ctx;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx)
                .addFilters(ctx.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    // --- Rutas publicas: responden sin token desde cualquier equipo -----------------------------

    @Test
    void elLoginDelDesktopNoPideToken() throws Exception {
        mvc.perform(desdeLaRed(post("/login"))).andExpect(status().isOk());
    }

    @Test
    void elHealthYLaVersionDelAutoUpdateNoPidenToken() throws Exception {
        // Si alguna de las dos pide token, el script de auto-update hace rollback.
        mvc.perform(desdeLaRed(get("/actuator/health"))).andExpect(status().isOk());
        mvc.perform(desdeLaRed(get("/api/version"))).andExpect(status().isOk());
    }

    @Test
    void lasSubscriptionsNoPidenTokenEnElHandshake() throws Exception {
        mvc.perform(desdeLaRed(get("/subscriptions"))).andExpect(status().isOk());
    }

    @Test
    void lasRutasPublicasYLasFacturasDelCentralNoPidenToken() throws Exception {
        mvc.perform(desdeLaRed(get("/public/sucursal-actual"))).andExpect(status().isOk());
        mvc.perform(desdeLaRed(post("/api/facturas-legales"))).andExpect(status().isOk());
    }

    // --- Google Drive: solo desde la misma maquina ----------------------------------------------

    @Test
    void googleDesdeOtraMaquinaSeRechaza() throws Exception {
        mvc.perform(desdeLaRed(get("/auth/google/clear-tokens"))).andExpect(status().isUnauthorized());
        mvc.perform(desdeLaRed(get("/auth/google/init"))).andExpect(status().isUnauthorized());
        mvc.perform(desdeLaRed(get("/auth/google/force-init"))).andExpect(status().isUnauthorized());
    }

    @Test
    void googleDesdeOtraMaquinaSeRechazaAunqueTraigaToken() throws Exception {
        mvc.perform(desdeLaRed(get("/auth/google/clear-tokens")).header("Authorization", TOKEN_BUENO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleDesdeLaMismaMaquinaFunciona() throws Exception {
        mvc.perform(desde("127.0.0.1", get("/auth/google/init"))).andExpect(status().isOk());
        mvc.perform(desde("0:0:0:0:0:0:0:1", get("/auth/google/init"))).andExpect(status().isOk());
    }

    // --- Backup: exige token aun desde la misma maquina (y el controller, ademas, ADMIN) ---------

    @Test
    void backupSinTokenSeRechazaDesdeCualquierLado() throws Exception {
        mvc.perform(desdeLaRed(post("/api/backup/trigger"))).andExpect(status().isUnauthorized());
        mvc.perform(desdeLaRed(get("/api/backup/test-pg-dump"))).andExpect(status().isUnauthorized());
        mvc.perform(desde("127.0.0.1", post("/api/backup/trigger"))).andExpect(status().isUnauthorized());
    }

    // --- Todo lo demas exige token --------------------------------------------------------------

    @Test
    void graphqlSinTokenDa401YConTokenPasa() throws Exception {
        mvc.perform(desdeLaRed(post("/graphql"))).andExpect(status().isUnauthorized());
        mvc.perform(desdeLaRed(post("/graphql")).header("Authorization", "Token malo"))
                .andExpect(status().isUnauthorized());
        mvc.perform(desdeLaRed(post("/graphql")).header("Authorization", TOKEN_BUENO))
                .andExpect(status().isOk());
    }

    @Test
    void unaRutaSinReglaNoQuedaAbiertaPorDefecto() throws Exception {
        mvc.perform(desdeLaRed(get("/ruta-nueva"))).andExpect(status().isUnauthorized());
        mvc.perform(desdeLaRed(get("/graphiql"))).andExpect(status().isUnauthorized());
        mvc.perform(desdeLaRed(get("/actuator/env"))).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder desdeLaRed(MockHttpServletRequestBuilder b) {
        return desde(IP_DE_LA_RED, b);
    }

    private static MockHttpServletRequestBuilder desde(String ip, MockHttpServletRequestBuilder b) {
        return b.with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {

        @Bean
        JwtAuthenticationEntryPoint entryPoint() {
            return new JwtAuthenticationEntryPoint();
        }

        @Bean
        RutasDePrueba rutasDePrueba() {
            return new RutasDePrueba();
        }
    }

    /**
     * Registra el provider ya armado: un singleton registrado asi no pasa por el autowiring, que
     * le pediria JwtValidator y UsuarioService (y detras, la base).
     */
    static class ProviderDePrueba implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            ctx.getBeanFactory().registerSingleton("authenticationProvider", providerQueAceptaSoloElTokenBueno());
        }
    }

    /** Acepta solo el token "bueno", como si JwtValidator lo hubiera validado. */
    private static JwtAuthenticationProvider providerQueAceptaSoloElTokenBueno() {
        JwtAuthenticationProvider provider = mock(JwtAuthenticationProvider.class);
        when(provider.supports(any())).thenReturn(true);
        when(provider.authenticate(any())).thenAnswer(inv -> {
            JwtAuthenticationToken t = inv.getArgument(0);
            if (!"bueno".equals(t.getToken())) throw new BadCredentialsException("token invalido");
            return new UsernamePasswordAuthenticationToken(
                    "usuario", null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        });
        return provider;
    }

    /** Responde 200 en todas las rutas: el test mide solo lo que decide la seguridad. */
    @RestController
    static class RutasDePrueba {
        @RequestMapping({"/login", "/public/**", "/api/facturas-legales", "/subscriptions",
                "/actuator/health", "/actuator/env", "/api/version", "/api/backup/**", "/auth/google/**",
                "/graphql", "/graphiql", "/ruta-nueva"})
        String ok() {
            return "ok";
        }
    }
}
