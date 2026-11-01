package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Registro de una caja cerrada que un ADMIN volvio a abrir. Insert-only: sube al central y no se
 * actualiza. Guarda el conteo de cierre que se descarto, que sigue existiendo como historial.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pdv_caja_reapertura", schema = "financiero")
public class PdvCajaReapertura implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pdv_caja_id")
    private Long pdvCajaId;

    private Long sucursalId;

    @Column(name = "conteo_cierre_anterior_id")
    private Long conteoCierreAnteriorId;

    @Column(name = "fecha_cierre_anterior")
    private LocalDateTime fechaCierreAnterior;

    private String motivo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @CreationTimestamp
    private LocalDateTime creadoEn;
}
