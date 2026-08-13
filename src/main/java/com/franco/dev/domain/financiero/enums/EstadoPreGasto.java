package com.franco.dev.domain.financiero.enums;

public enum EstadoPreGasto {
    PENDIENTE, TRAMITE, AUTORIZADO, RECHAZADO, COMPLETADO, ENVIADO_A_TESORERIA,
    /** Paridad con central (PR feat/modulo-financiero). pre_gasto no replica, así que
     *  no es un riesgo de valueOf, pero se alinea el enum por consistencia. */
    PAGADO
}