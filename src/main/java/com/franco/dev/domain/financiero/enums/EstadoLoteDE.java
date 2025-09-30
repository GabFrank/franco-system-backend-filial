package com.franco.dev.domain.financiero.enums;

public enum EstadoLoteDE {
    PENDIENTE_ENVIO,      // Creado, con DEs asignados, pero aún no enviado a SIFEN.
    EN_PROCESO,           // Lote enviado y recibido exitosamente por SIFEN, esperando el resultado.
    PROCESADO,            // Se ha consultado el resultado del lote, y todos los DEs dentro de él han sido actualizados.
    PROCESADO_CON_ERRORES,// Se consultó el resultado, pero hubo inconsistencias.
    ERROR_ENVIO,          // Falló el envío del lote por un problema de comunicación. Se reintentará.
    ERROR_RED,            // Error de conectividad/red. No se reintenta hasta que se restablezca la conexión.
    ERROR_PERMANENTE,     // El lote superó el número de reintentos y requiere intervención manual.
    RECHAZADO             // El lote fue rechazado en el envío inicial.
}
