package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreGastoDetalleFinanzas implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Moneda moneda;
    private String formaPago;
    private BigDecimal monto;
    private LocalDateTime creadoEn;
}
