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
public class RetiroDto {
    Long id;
    Long cajaId;
    LocalDateTime fecha;
    Usuario usuario;
    Funcionario responsable;
    Double retiroGs;
    Double retiroRs;
    Double retiroDs;
}
