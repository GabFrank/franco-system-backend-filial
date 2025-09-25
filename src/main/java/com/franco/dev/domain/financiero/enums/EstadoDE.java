package com.franco.dev.domain.financiero.enums;

public enum EstadoDE {
    PENDIENTE, // Creado y listo para ser incluido en un lote.
    EN_LOTE,   // Ha sido asignado a un lote para su envío.
    APROBADO,  // Confirmado por SIFEN como válido.
    RECHAZADO, // Rechazado por SIFEN.
    CANCELADO  // Anulado por el usuario.
}
