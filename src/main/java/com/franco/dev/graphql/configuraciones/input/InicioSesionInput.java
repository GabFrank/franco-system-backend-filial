package com.franco.dev.graphql.configuraciones.input;

import com.franco.dev.domain.configuracion.enums.TipoDispositivo;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class InicioSesionInput {
    private Long id;
    private Long usuarioId;
    private Long sucursalId;
    private TipoDispositivo tipoDespositivo;
    private String idDispositivo;
    private String token;
    private String horaInicio;
    private String horaFin;
    private String creadoEn;
}
