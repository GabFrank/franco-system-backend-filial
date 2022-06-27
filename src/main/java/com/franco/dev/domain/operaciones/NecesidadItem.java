package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.operaciones.enums.NecesidadEstado;
import com.franco.dev.domain.operaciones.enums.NecesidadItemEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(
        name = "necesidad_item_estado",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "necesidad_item", schema = "operaciones")
public class NecesidadItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "necesidad_id", nullable = true)
    private Necesidad necesidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = true)
    private Producto producto;

    private Boolean autogenerado;

    @Column(name = "cantidad_sugerida")
    private Float cantidadSugerida;

    private Boolean modificado;

    private Float cantidad;

    private Boolean frio;

    private String observacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type( type = "necesidad_item_estado")
    private NecesidadItemEstado estado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



