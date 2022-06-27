package com.franco.dev.graphql.personas.input;

import com.franco.dev.domain.general.enums.DiasSemana;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class ProveedorDiasVisitaInput {
    private Long id;

    private Boolean credito;

    private Long proveedorId;

    private DiasSemana dia;

    private Integer hora;

    private String observacion;

    private Long usuarioId;
}
