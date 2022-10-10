package com.franco.dev.domain.productos;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.rabbit.RabbitEntity;
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
        name = "tipo_conservacion",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "producto", schema = "productos")
public class Producto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private Boolean propagado;
    private String descripcion;
    private String descripcionFactura;
    private Integer iva;
    private Integer unidadPorCaja;
    private Integer unidadPorCajaSecundaria;
    private Boolean balanza;
    private Boolean garantia;
    private Integer tiempoGarantia;
    private Boolean ingrediente;
    private Boolean combo;
    private Boolean stock;
    private Boolean cambiable;
    private Boolean promocion;
    private Boolean vencimiento;
    private Integer diasVencimiento;
    private String observacion;
    private String imagenes;
    private Boolean isEnvase;



    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_conservacion")
    @Type( type = "tipo_conservacion")
    private TipoConservacion tipoConservacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_familia_id", nullable = true)
    private Subfamilia subfamilia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "envase_id", nullable = true)
    private Producto envase;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    public String toString(){
        String texto = "Id: " + this.id + "/n" + "Descripcion: " + this.descripcion;
        return texto;
    }
}



