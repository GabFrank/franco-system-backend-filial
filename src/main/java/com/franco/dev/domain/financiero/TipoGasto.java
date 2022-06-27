package com.franco.dev.domain.financiero;

import com.franco.dev.domain.empresarial.Cargo;
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
@Table(name = "tipo_gasto", schema = "financiero")
public class TipoGasto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Boolean isClasificacion;
    private Boolean activo;
    private Boolean autorizacion;

    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clasificacion_gasto_id", nullable = true)
    private TipoGasto clasificacionGasto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id", nullable = true)
    private Cargo cargo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



