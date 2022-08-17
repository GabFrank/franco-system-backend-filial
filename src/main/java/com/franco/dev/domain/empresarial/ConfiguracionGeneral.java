package com.franco.dev.domain.empresarial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_general", schema = "empresarial")
public class ConfiguracionGeneral implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String nombreEmpresa;

    private String razonSocial;

    private String ruc;

    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    @JsonIgnore
    private Usuario usuario;

}