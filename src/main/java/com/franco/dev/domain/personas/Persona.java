package com.franco.dev.domain.personas;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.franco.dev.domain.general.Ciudad;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "persona", schema = "personas")
public class Persona implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private String nombre;
    private String apodo;
    private LocalDateTime nacimiento;
    private String documento;
    private String sexo;
    private String direccion;
    private String telefono;
    private String socialMedia;
    private String imagenes;
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ciudad_id", nullable = true)
    @JsonIgnore
    private Ciudad ciudad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    @JsonIgnore
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}



