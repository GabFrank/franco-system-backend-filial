package com.franco.dev.graphql.financiero.publisher;

import lombok.Data;

/**
 * Aviso de que una captura de cupon termino. Es un <b>timbre, no el contenido</b>.
 *
 * <p><b>No lleva el token, y eso es el punto.</b> Por WebSocket no hay {@code SecurityContext} en
 * esta aplicacion, asi que toda subscription del filial es de hecho anonima y lo que se emita lo
 * escucha cualquiera con un socket contra el filial. El token es la credencial completa: con el en
 * la mano, {@code capturaCupon(token)} --que solo exige estar logueado, no ser el dueño de la
 * captura-- devuelve el texto leido del cupon, con codigo de autorizacion y monto. Difundirlo
 * dejaba que cualquier empleado con sesion abierta leyera las ventas con tarjeta de las otras
 * cajas de la sucursal.
 *
 * <p>El desktop no lo necesita: el token lo tiene desde que pidio la captura, y lo que hacia con
 * el del aviso era compararlo contra el suyo. Le alcanza con saber <b>de que caja</b> es la
 * novedad; el token para pedir el contenido ya lo tiene. Y si un aviso se pierde o llega uno
 * ajeno, el sondeo cada 3 segundos lo cubre igual.
 *
 * <p>Se emite tambien en {@code ERROR}, porque el cajero suele estar mirando la caja y no el
 * telefono; el motivo lo trae la query.
 */
@Data
public class CapturaCuponUpdate {

    /** En una sucursal hay varias cajas escuchando el mismo canal. Es el unico filtro que viaja. */
    private Long cajaId;

    /** ESPERANDO / PROCESANDO / LISTO / ERROR. */
    private String estado;
}
