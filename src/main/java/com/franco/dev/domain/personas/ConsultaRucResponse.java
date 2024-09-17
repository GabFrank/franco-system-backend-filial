package com.franco.dev.domain.personas;

import lombok.Data;

@Data
public class ConsultaRucResponse {
    private Boolean procesamientoCorrecto;
    private String mensajeProcesamiento;
    private String ruc;
    private String dv;
    private String estado;
    private String nombre;
    private String nombreFantasia;
    private String telefono;
    private String direccion;
    private Integer codigoEstablecimiento;
    private Boolean validacionCorrecta;
    private String mensajeValidacion;
}
