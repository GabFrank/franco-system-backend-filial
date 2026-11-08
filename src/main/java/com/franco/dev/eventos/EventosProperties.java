package com.franco.dev.eventos;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuracion de los eventos por webhook (filial #246). Vive en el overlay application.properties
 * de cada nodo: ni la clave privada ni los secretos de cabecera pasan por la base ni por la replica.
 */
@Data
@Component
@ConfigurationProperties(prefix = "frc.webhooks")
public class EventosProperties {

    /** Corte duro por nodo. Apagado, no se emite ni se envia; lo pendiente queda en la tabla. */
    private boolean enabled = false;

    /** En los tests: emitir sin transaccion activa lanza excepcion en vez de abrir una propia. */
    private boolean estricto = false;

    /** Empresa para el source de CloudEvents: frc://<empresa>/sucursal/<id>. */
    private String empresa = "bodega";

    /** Base del link al detalle (opcional), sin barra final. */
    private String linkBase;

    /** Clave privada Ed25519 del nodo: la semilla de 32 bytes en base64. */
    private String clavePrivada;

    /** Identificador de la clave publica que publica el central (p. ej. "suc7-v1"). */
    private String claveId;

    /** Valores de las cabeceras fijas, por nombre de referencia (secreto_ref de la suscripcion). */
    private Map<String, String> secretos = new HashMap<>();

    /** Si no esta vacia, solo se envia a estos hosts (exactos). Es la defensa contra DNS rebinding. */
    private List<String> hostsPermitidos = new ArrayList<>();

    /** Envios simultaneos del nodo. */
    private int maxConcurrentes = 10;

    /** Envios simultaneos por suscripcion: un receptor lento no frena a los demas. */
    private int maxPorDestino = 2;

    /** Eventos por POST cuando la suscripcion pide entrega por lote. */
    private int tamanoLote = 100;

    private int timeoutMs = 5000;

    private int leaseSegundos = 60;
}
