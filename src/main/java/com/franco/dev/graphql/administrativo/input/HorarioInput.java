package com.franco.dev.graphql.administrativo.input;

import lombok.Data;

@Data
public class HorarioInput {
    private Long id;
    private String descripcion;
    private String horaEntrada;
    private String horaSalida;
    private Integer toleranciaMinutos;
    private String inicioDescanso;
    private String finDescanso;
    private Long usuarioId;
    private java.util.List<String> dias;
    private com.franco.dev.domain.administrativo.enums.Turno turno;
}
