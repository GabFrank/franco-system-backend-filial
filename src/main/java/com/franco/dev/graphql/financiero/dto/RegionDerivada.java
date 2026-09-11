package com.franco.dev.graphql.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Una region que el filial derivo de un cupon de muestra, para que el administrador la revise.
 *
 * <p><b>Es una propuesta, no una fila.</b> Las regiones viven en central, que es el publisher de
 * {@code MAIN_TO_ALL}; este repo tiene el OCR y las fotos. El filial deriva y devuelve esto, el
 * desktop lo muestra, y recien si el administrador acepta, central lo guarda.
 *
 * <p>{@link #sinRegion} no es un error: es un campo que el patron capturo pero cuya posicion no
 * se pudo determinar sin inventar. Ese campo se va a resolver por patron, sin restriccion
 * espacial, y el mapa queda parcial — que es valido y preferible a una region mal puesta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegionDerivada {

    /** El nombre del grupo del patron: destino canonico o clave libre. */
    private String campo;

    /** La etiqueta impresa que ancla la region. Null si no se encontro ninguna. */
    private String etiqueta;

    /** DERECHA | ABAJO | DENTRO. */
    private String posicion;

    /** Lo que se leyo en el cupon de muestra. Sirve para que el humano verifique de un vistazo. */
    private String valorLeido;

    private BigDecimal x1;
    private BigDecimal y1;
    private BigDecimal x2;
    private BigDecimal y2;

    /** Null si se derivo bien; si no, por que no se pudo. */
    private String sinRegion;
}
