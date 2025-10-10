package com.franco.dev.domain.financiero.enums;

/**
 * Estados posibles de un evento de SIFEN.
 */
public enum EstadoEvento {
    PENDIENTE,          // Evento enviado, esperando procesamiento
    APROBADO,           // Evento aprobado por SIFEN
    RECHAZADO,          // Evento rechazado por SIFEN
    ERROR_ENVIO         // Error al enviar el evento
}

