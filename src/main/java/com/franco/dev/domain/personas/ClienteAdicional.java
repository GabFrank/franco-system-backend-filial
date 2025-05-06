package com.franco.dev.domain.personas;

import com.fasterxml.jackson.annotation.JsonView;
import com.franco.dev.utilitarios.JsonIdView;
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
@Table(name = "cliente_adicional", schema = "personas")
public class ClienteAdicional implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @JsonView(JsonIdView.Id.class)
    private Long id;

    private Float credito;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "persona_id", nullable = true)
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}



