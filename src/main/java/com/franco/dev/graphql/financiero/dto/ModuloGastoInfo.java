package com.franco.dev.graphql.financiero.dto;

import com.franco.dev.domain.financiero.enums.TipoPadreGastoModulo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catálogo de módulos padre de tipo de gasto con las banderas de comportamiento
 * derivadas de las reglas de negocio del backend. Es la única fuente de verdad
 * consumida por el frontend para poblar selects y decidir la UI.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModuloGastoInfo {

    private TipoPadreGastoModulo valor;
    private String etiqueta;
    private String grupo;
    private Boolean esServicioContinuo;
    private Boolean tieneCuotasActivo;
    private Boolean requiereEnteActivo;
    private String tipoEnteEsperado;
    private Boolean diaVencimientoEnContinuo;
    private Boolean lecturaMedidorEnContinuo;
    private Boolean nisEnContinuo;
}
