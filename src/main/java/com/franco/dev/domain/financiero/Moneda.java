package com.franco.dev.domain.financiero;

import com.fasterxml.jackson.annotation.JsonView;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.JsonIdView;
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
@Table(name = "moneda", schema = "financiero")
public class Moneda implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @JsonView(JsonIdView.Id.class)
    private Long id;

    @Column(name = "denominacion")
    private String denominacion;

    private String simbolo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pais_id", nullable = true)
    private Pais pais;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    // Campos de "Moneda rica" (modulo financiero central). Columnas agregadas por V88.3;
    // se exponen para alinear el contrato GraphQL con el desktop feat/modulo-financiero.
    private Boolean activo;

    private Boolean principal;

    private Integer decimales;

    @Column(name = "regla_redondeo")
    private String reglaRedondeo;

    @Column(name = "redondeo_multiplo")
    private java.math.BigDecimal redondeoMultiplo;

    public String getAbreviatura() {
        switch (denominacion) {
            case "GUARANI":
                return "Gs";
            case "REAL":
                return "Rs";
            case "DOLAR":
                return "Us";
            default:
                return denominacion.substring(0, 3);
        }
    }
}



