package com.franco.dev.domain.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Funcionario;
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
@Table(name = "gasto", schema = "financiero")
public class Gasto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sucursalId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "responsable_id", nullable = true)
    private Funcionario responsable;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "caja_id", nullable = true)
    private PdvCaja caja;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_gasto_id", nullable = true)
    private TipoGasto tipoGasto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Funcionario autorizadoPor;

    private String observacion;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean activo;

    private Boolean finalizado;

    private Double retiroGs;
    private Double retiroRs;
    private Double retiroDs;
    private Double vueltoGs;
    private Double vueltoRs;
    private Double vueltoDs;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_vuelto_id", nullable = true)
    private Sucursal sucursalVuelto;
}



