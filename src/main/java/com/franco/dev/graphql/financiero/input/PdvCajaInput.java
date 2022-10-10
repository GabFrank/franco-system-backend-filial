package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.operaciones.enums.TipoEntrada;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PdvCajaInput {
    private Long id;
    private Long sucursalId;
    private String descripcion;
    private String observacion;
    private Boolean activo;
    private Boolean tuvoProblema;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    private TipoEntrada tipoEntrada;
    private Long maletinId;
    private Long conteoAperturaId;
    private Long conteoCierreId;
    private LocalDateTime creadoEn;
    private Long usuarioId;
    private Double totalGs;
    private Double totalRs;
    private Double totalDs;
    private Boolean verificado;
    private Long verificadoPorId;
}
