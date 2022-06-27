package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CajaBalance {
    private Long cajaId;
    private Double totalAperGs;
    private Double totalAperRs;
    private Double totalAperDs;
    private Double totalCierreGs;
    private Double totalCierreRs;
    private Double totalCierreDs;
    private Double totalVentaGs;
    private Double totalVentaRs;
    private Double totalVentaDs;
    private Double totalTarjeta;
    private Double totalDescuento;
    private Double totalAumento;
    private Double totalRetiroGs;
    private Double totalRetiroRs;
    private Double totalRetiroDs;
    private Double totalGastoGs;
    private Double totalGastoRs;
    private Double totalGastoDs;
    private Double totalCanceladas;
}



