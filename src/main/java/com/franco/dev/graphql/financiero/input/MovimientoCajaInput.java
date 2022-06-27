package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;

@Data
public class MovimientoCajaInput {
    private Long id;

    private Long referencia;

    private Double cantidad;

    private String nombre;

    private Long pdvCajaId;

    private Long monedaId;

    private Long cambioId;

    private PdvCajaTipoMovimiento tipoMovimiento;

    private Date creadoEn;

    private Long usuarioId;

    private Boolean activo;
}
