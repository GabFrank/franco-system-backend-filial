package com.franco.dev.service.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Registra cuando el {@code usuarioId} que manda el cliente no es el de la sesion (issue #142).
 *
 * <p><b>Esta fase solo observa.</b> No rechaza ni sobreescribe nada: el objetivo es entrar a la
 * fase de enforcement con evidencia de produccion, porque {@code usuarioId} no significa lo mismo
 * en todos los resolvers. En venta o caja es «quien firma»; en proveedor es «quien creo», que el
 * desktop preserva al editar a proposito; en marcacion la identidad la da el rostro y no la sesion.
 * Sobreescribir parejo romperia los dos ultimos.
 *
 * <p>La evidencia se lee grepeando {@link #MARCADOR} en el log del filial.
 */
@Service
public class AuditorUsuarioId {

    private static final Logger log = LoggerFactory.getLogger(AuditorUsuarioId.class);

    /** Marcador fijo para poder grepear el log sin arrastrar otras lineas. */
    public static final String MARCADOR = "AUDIT-USUARIOID";

    static final String PROPERTY_HABILITADO = "auditoria.usuarioid.habilitado";

    @Autowired
    private UsuarioSesionService usuarioSesionService;

    @Autowired
    private Environment env;

    /**
     * Compara el usuario del input contra el de la sesion y registra la diferencia.
     *
     * <p><b>No lanza nunca y no cambia nada.</b> Un instrumento de observabilidad que tumba una
     * venta es peor que el problema que mide. La otra mitad de esa garantia no esta aca sino en
     * {@link UsuarioSesionService}, que corre fuera de la transaccion: tragarse la excepcion no
     * alcanzaria si el SELECT dejara la transaccion de la venta marcada rollback-only.
     *
     * @param operacion nombre del punto de entrada, p. ej. {@code "VentaGraphQL.saveVenta"}
     * @param usuarioIdDelInput el que vino en el input del cliente
     */
    public void verificar(String operacion, Long usuarioIdDelInput) {
        try {
            if (!habilitado() || usuarioIdDelInput == null) {
                return;
            }
            Optional<Long> usuarioIdDeLaSesion = usuarioSesionService.idDelUsuarioAutenticado();
            if (!usuarioIdDeLaSesion.isPresent()) {
                // Sin identidad en el contexto. En los resolvers HTTP de esta fase eso es un
                // proceso de sistema (schedulers, replicacion): no es un hallazgo y registrarlo
                // llenaria el log de ruido.
                return;
            }
            if (usuarioIdDeLaSesion.get().equals(usuarioIdDelInput)) {
                return;
            }
            log.warn("{} operacion={} usuarioInput={} usuarioSesion={} sucursal={}",
                    MARCADOR, operacion, usuarioIdDelInput, usuarioIdDeLaSesion.get(),
                    env == null ? null : env.getProperty("sucursalId"));
        } catch (Exception e) {
            // A proposito: cualquier falla del auditor se queda aca. No se usa el logger en WARN
            // para no convertir un problema del instrumento en ruido sobre la evidencia.
            log.debug("{} el auditor fallo en {}: {}", MARCADOR, operacion, e.toString());
        }
    }

    private boolean habilitado() {
        if (env == null) {
            return true;
        }
        return !"false".equalsIgnoreCase(env.getProperty(PROPERTY_HABILITADO, "true"));
    }

    void setUsuarioSesionService(UsuarioSesionService usuarioSesionService) {
        this.usuarioSesionService = usuarioSesionService;
    }

    void setEnv(Environment env) {
        this.env = env;
    }
}
