package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentaPorPeriodoV1Dto {
    private LocalDateTime creadoEn;
    private Double valorGs = 0.0;
    private Double valorRs = 0.0;
    private Double valorDs = 0.0;
    private Double valorTotalGs = 0.0;
    private Integer cantVenta;

    public void addGs(Double valor){
        valorGs = valorGs + valor;
    }
    public void addRs(Double valor){
        valorRs = valorRs + valor;
    }
    public void addDs(Double valor){
        valorDs = valorDs + valor;
    }
    public void addTotalGs(Double valor){
        valorTotalGs = valorTotalGs + valor;
    }
}
