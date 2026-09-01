package com.franco.dev.dto.factura;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos del cliente que el servidor central manda junto con la factura.
 * <p>
 * En esta arquitectura personas y clientes no llegan por replicacion logica a todas las
 * filiales, asi que el filial puede no conocer al cliente que el central eligio. Con
 * estos datos puede materializarlo localmente en vez de guardar la factura con
 * cliente_id null.
 * <p>
 * Los ids son los del central, que es el dueño del dato: el filial los respeta tal cual
 * para que la fila coincida cuando llegue por replicacion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteFacturaDTO {
    private Long id;
    private String tipo;
    private Float credito;
    private String codigo;
    private Boolean tributa;
    private Boolean verificadoSet;
    private Boolean activo;

    private Long personaId;
    private String personaNombre;
    private String personaApodo;
    private String personaDocumento;
    private String personaSexo;
    private String personaDireccion;
    private String personaTelefono;
    private String personaEmail;
}
