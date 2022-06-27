package com.franco.dev.domain.operaciones.enums;

public enum NecesidadEstado {

    ACTIVO, //CUANDO EL PEDIDO SE CONCLUYO PERO TODAVIA NO LLEGO
    MODIFICADO, //CUANDO EL PEDIDO SE MODIFICO PERO TODAVIA NO LLEGO
    CANCELADO, //PEDIDO FUE CANCELADO ANTES DE LLEGAR
    EN_VERIFICACION, //CUANDO EL PEDIDO YA LLEGO PERO SE ESTA VERIFICANDO
    EN_VERIFICACION_SOLICITUD_AUTORIZACION, // CUANDO EL PEDIDO ESTA EN VERIFICACION Y SE SOLICITA AUTORIZACION
    VERFICADO_SIN_MODIFICACION, //CUANDO EL PEDIDO YA LLEGO Y YA FUE VERIFICADO CON MODIFICACIONES PERO AUN NO SE PAGO
    VERFICADO_CON_MODIFICACION, //CUANDO EL PEDIDO YA LLEGO Y YA FUE VERIFICADO SIN MODIFICACIONES PERO AUN NO SE PAGO
    CONCLUIDO //CUANDO EL PEDIDO YA LLEGO, YA SE VERIFICO Y YA GENERO UNA COMPRA

}
