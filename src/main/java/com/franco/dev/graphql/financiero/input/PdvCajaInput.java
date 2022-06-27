package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.domain.operaciones.enums.TipoEntrada;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class PdvCajaInput {
    private Long id;
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
}
