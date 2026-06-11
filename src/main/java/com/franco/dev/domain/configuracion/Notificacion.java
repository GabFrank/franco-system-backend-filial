package com.franco.dev.domain.configuracion;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Notificacion implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String titulo;
    private String mensaje;
    private String tipo;
    private String data;
    private String estadoTablero;
    private Usuario verificadoPorUsuario;
    private LocalDateTime fechaVerificacion;
    private Usuario usuarioCreador;
    private LocalDateTime creadoEn;
}
