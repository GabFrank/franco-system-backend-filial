package com.franco.dev.graphql.financiero.publisher;

import lombok.Data;

/**
 * Aviso de que una captura de cupon termino. Es un <b>timbre, no el contenido</b>.
 *
 * <p>Lleva lo justo para que un desktop reconozca si el aviso es suyo. El texto leido del cupon
 * --PAN enmascarado, codigo de autorizacion, monto-- NO viaja por aca: se pide con la query
 * {@code capturaCupon(token)}, que si pasa por login.
 *
 * <p><b>Por que.</b> Por WebSocket no hay {@code SecurityContext} en esta aplicacion, asi que toda
 * subscription del filial es de hecho anonima: cualquiera que abra un socket contra el filial en
 * la LAN escucha lo que se emita. Un timbre no le sirve de nada a quien no tenga sesion para
 * cambiarlo por contenido.
 *
 * <p>Se emite tambien en {@code ERROR}, porque el cajero suele estar mirando la caja y no el
 * telefono; el motivo lo trae la query.
 */
@Data
public class CapturaCuponUpdate {

    private String token;

    /** En una sucursal hay varias cajas escuchando el mismo canal. El filtro real es el token. */
    private Long cajaId;

    /** ESPERANDO / PROCESANDO / LISTO / ERROR. */
    private String estado;
}
