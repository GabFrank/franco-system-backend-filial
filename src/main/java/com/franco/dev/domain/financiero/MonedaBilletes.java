package com.franco.dev.domain.financiero;

import com.franco.dev.domain.personas.Usuario;
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
@Table(name = "moneda_billetes", schema = "financiero")
public class MonedaBilletes implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Boolean flotante;
    private Boolean papel;
    private Double valor;
    private Boolean activo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



