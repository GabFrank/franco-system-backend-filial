package com.franco.dev.graphql.administrativo.input;

import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MarcacionInput {
    private Long id;
    private Long usuarioId;
    private TipoMarcacion tipo;

    private BigDecimal latitud;
    private BigDecimal longitud;
    private Float precisionGps;
    private Integer distanciaSucursalMetros;

    private String deviceId;
    private String deviceInfo;

    private Long sucursalEntradaId;
    private String fechaEntrada;
    private Long sucursalSalidaId;
    private String fechaSalida;

    private String codigo;
    private java.util.List<Double> embedding;
    private Boolean esSalidaAlmuerzo;
}