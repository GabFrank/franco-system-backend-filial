package com.franco.dev.domain.operaciones.enums;

public enum CompraEstado {

    ACTIVO, // LA COMPRA FUE GUARDADA CON EXITO E INTERFIERE EN EL STOCK
    CANCELADO, // LA COMPRA FUE CANCELADA Y NO INTERFIERE EN EL STOCK
    DEVOLVIDO, // LA COMPRA FUE DEVOLVIDA Y NO INTERFIERE EN EL STOCK
    EN_OBSERVACION, // LA COMPRA FUE ENVIADA A OBSERVACION POR SOSPECHA DE ALGUNA IRREGULARIDAD
    IRREGULAR, // FUE DETECTADO ALGUNA IRREGULARIDAD EN LA COMPRA
    PRE_COMPRA, //EL PRIMER ESTADO DERIVADO DE UN PEDIDO

}
