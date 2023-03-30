package com.franco.dev.domain.personas;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonView;
import com.franco.dev.domain.GenericDomain;
import com.franco.dev.utilitarios.JsonIdView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Table(name = "usuario", schema = "personas")
public class Usuario extends GenericDomain implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @JsonView(JsonIdView.Id.class)
    private Long id;

    private String password;

    private String nickname;
    private String email;

    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "persona_id", nullable = true)
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    @JsonBackReference
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

}




