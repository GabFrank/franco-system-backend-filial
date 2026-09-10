package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.graphql.financiero.dto.CapturaCuponQr;
import com.franco.dev.service.financiero.CapturaCuponService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * El lado del desktop de la captura de la foto del cupon.
 *
 * <p>El desktop pide una captura, muestra la URL que vuelve dentro de un QR, y espera. El
 * telefono no pasa por aca: sube la foto por REST a {@code /public/captura/{token}}, porque del
 * otro lado hay un navegador pelado sin Apollo ni sesion (ver {@code CapturaCuponController}).
 *
 * <p><b>Escuchar y preguntar.</b> El aviso llega por {@code capturaCuponSub} --que vive en
 * {@code CapturaCuponSubscription}, por el pointcut del aspecto de seguridad-- pero es un timbre
 * sin contenido y ademas el observable es caliente: quien no estaba suscrito en ese instante se
 * lo pierde. {@code capturaCupon(token)} es las dos cosas: la unica via del contenido, y la red
 * de contencion del aviso perdido. El desktop la consulta al recibir el timbre y cada tantos
 * segundos mientras espera. Caso 4 de §2.10 de FASE-2-TICKET-FISICO.md.
 */
@Component
public class CapturaCuponGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CapturaCuponService service;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Abre una captura y devuelve lo necesario para el QR.
     *
     * <p>Va con seguridad normal: lo pide un desktop logueado. La puerta del telefono es otra
     * --el token-- y se apoya justamente en que esta mutation ya exigio sesion.
     */
    public CapturaCuponQr crearCapturaCupon(Long cajaId, Long sucursalId, Long usuarioId) {
        CapturaCupon c = service.crear(cajaId, sucursalId,
                usuarioId == null ? null : usuarioService.findById(usuarioId).orElse(null));
        return new CapturaCuponQr(c.getToken(), service.urlDe(c), c.getExpiraEn().toString());
    }

    /** Estado actual de una captura. Es la via de respaldo de la subscription. */
    public CapturaCupon capturaCupon(String token) {
        return service.porToken(token).orElse(null);
    }
}
