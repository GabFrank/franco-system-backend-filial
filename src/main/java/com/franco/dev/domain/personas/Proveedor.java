package com.franco.dev.domain.personas;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "proveedor", schema = "personas")
public class Proveedor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Boolean credito;

    @Column(name = "tipo_credito")
    private String tipoCredito;

    @Column(name = "cheque_dias")
    private Integer chequeDias;

    @Column(name = "datos_bancarios_id")
    private Long datosBancarios;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = true)
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_encargado_id", nullable = true)
    private Funcionario funcionarioEncargado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}



