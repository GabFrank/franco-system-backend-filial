package com.franco.dev.graphql.personas.input;

import lombok.Data;

import java.util.Date;

@Data
public class PersonaUpdate {

    private Long id;
    private String nombre;
    private String apodo;
    private String sexo;
    private Date nacimiento;
    private String documento;
    private String email;
    private String direccion;
    private Integer ciudad;
    private String telefono;
    private String socialMedia;
    private String imagenes;
    private Long usuarioId;

}
