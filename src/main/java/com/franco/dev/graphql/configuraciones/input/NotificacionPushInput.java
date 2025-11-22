package com.franco.dev.graphql.configuraciones.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
//@Entity
//@Table(name = "notificacion_push")
public class NotificacionPushInput {
    //    public class NotificacionPush implements Serializable {
//
//    private static final long serialVersionUID = 1L;
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
//
//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "usuario_id", nullable = false)
    private Long personaId;
//
//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "role_id", nullable = false)
    private Long roleId;
//
//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "cargo_id", nullable = false)
    private Long cargoId;
//
//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "tipo_cliente_id", nullable = false)
    private Long tipoClienteId;

    private String titulo;
    private String mensaje;
    private String token;
    private String data;

    //    @Column(name = "creado_en")
    private String creadoEn;
}
