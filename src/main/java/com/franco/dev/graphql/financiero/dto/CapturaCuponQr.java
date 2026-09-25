package com.franco.dev.graphql.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que el desktop necesita para dibujar el QR de la captura.
 *
 * <p>La URL la arma el filial, no el desktop: el desktop no sabe por que interfaz lo alcanza el
 * telefono --puede haber LAN, tailscale y docker en la misma maquina-- y no tendria como
 * elegir. Ver {@code CapturaCuponService.urlDe}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CapturaCuponQr {

    private String token;

    /** {@code http://<ip-lan>:<puerto>/public/captura/<token>}. Es lo que se codifica en el QR. */
    private String url;

    /** ISO local. Pasado esto el QR no sirve mas y hay que pedir otro. */
    private String expiraEn;
}
