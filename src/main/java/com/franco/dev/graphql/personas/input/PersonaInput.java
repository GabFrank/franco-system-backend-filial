package com.franco.dev.graphql.personas.input;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
public class PersonaInput {

    private Long id;
    private String nombre;
    private String apodo;
    private String sexo;
    private Date nacimiento;
    private String documento;
    private String email;
    private String direccion;
    private Long ciudadId;
    private String telefono;
    private String socialMedia;
    private String imagenes;
    private LocalDateTime creadoEn;
    private Long usuarioId;

}
