package com.franco.dev.domain.financiero;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "timbrado", schema = "financiero")
public class Timbrado implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String razonSocial;

    private String ruc;

    private String numero;

    private Boolean isElectronico;

    private String csc;

    private String cscId;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    // Campos para documento electrónico
    private String email;
    private String tipoSociedad;
    private String domicilioFiscalDepartamento;
    private String domicilioFiscalCiudad;
    private String domicilioFiscalCodigoCiudad;
    private String domicilioFiscalLocalidad;
    private String domicilioFiscalBarrio;
    private String domicilioFiscalDireccion;
    private String telefono;
    private String codActividadEconomicaPrincipal;
    private String descActividadEconomicaPrincipal;
    private String listCodigoActividadEconomicaSecundaria;
    private String listDescripcionActividadEconomicaSecundaria;

    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



