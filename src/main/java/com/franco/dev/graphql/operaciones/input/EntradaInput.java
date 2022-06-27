package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.enums.TipoEntrada;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
public class EntradaInput {
    private Long id;
    private Long responsableCargaId;
    private Long sucursalId;
    private String observacion;
    private TipoEntrada tipoEntrada;
    private Long usuarioId;
    private Boolean activo;
    private LocalDateTime creadoEn;
}
