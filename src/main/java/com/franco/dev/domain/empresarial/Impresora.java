package com.franco.dev.domain.empresarial;

import com.franco.dev.domain.empresarial.enums.PerfilPapel;
import com.franco.dev.domain.empresarial.enums.TipoConexion;
import com.franco.dev.domain.empresarial.enums.TipoImpresora;
import com.franco.dev.domain.empresarial.enums.UsoImpresora;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Registro de impresoras. En la filial esta entidad es de solo lectura: las filas se
 * escriben en el servidor central (administrativo) y se replican hacia la filial via
 * replicacion logica. Por eso el id no se autogenera aca (llega desde central).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "impresora", schema = "empresarial")
public class Impresora implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String nombre;

    private Boolean activo;

    private Boolean esPredeterminada;

    /** Host donde esta conectada fisicamente la impresora (routing). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @Enumerated(EnumType.STRING)
    private TipoImpresora tipo;

    @Enumerated(EnumType.STRING)
    private UsoImpresora uso;

    @Enumerated(EnumType.STRING)
    private TipoConexion conexion;

    /** Nombre de cola CUPS (conexion CUPS/USB/BLUETOOTH). */
    private String colaCups;

    /** Direccion IP (conexion RED / inalambrica). */
    private String ip;

    /** Puerto RAW/JetDirect (default 9100) para conexion RED. */
    private Integer puerto;

    @Enumerated(EnumType.STRING)
    private PerfilPapel perfilPapel;

    /** Columnas de caracteres para termicas (derivado del perfil, editable). */
    private Integer columnas;

    /** Ancho/alto en mm para hoja/custom (impresoras normales). */
    private Integer anchoMm;
    private Integer altoMm;

    /** Marca: EPSON | STAR | BEMATECH | HP | ... */
    private String marca;

    /** Code-page ESC/POS. */
    private String codepage;

    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
