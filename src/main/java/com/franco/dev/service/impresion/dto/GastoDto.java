package com.franco.dev.service.impresion.dto;

import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GastoDto {
    Long id;
    LocalDateTime fecha;
    Long cajaId;
    Usuario usuario;
    Funcionario responsable;
    Funcionario autorizadoPor;
    TipoGasto tipoGasto;
    Double retiroGs;
    Double retiroRs;
    Double retiroDs;
    String observacion;
    Double descuento;
    Double aumento;
    Double vueltoGs;
    Double vueltoRs;
    Double vueltoDs;
}
