package com.franco.dev.service.personas.dto;

import lombok.Data;

@Data
public class PersonaSyncRequest {

    private Long id;
    private String nombre;
    private String apodo;
    private String sexo;
    private String nacimiento;
    private String documento;
    private String email;
    private String direccion;
    private Long ciudadId;
    private String telefono;
    private String socialMedia;
    private String imagenes;
    private Long usuarioId;
}

