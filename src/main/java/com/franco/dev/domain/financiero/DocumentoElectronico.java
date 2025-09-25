package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(
        name = "estado",
        typeClass = PostgreSQLEnumType.class
)
@Entity
@Table(name = "documento_electronico", schema = "financiero")
public class DocumentoElectronico implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sucursalId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_legal_id", nullable = false, unique = true)
    private FacturaLegal facturaLegal;

    // Información del documento electrónico
    private String cdc;
    private String urlQr;
    @Column(columnDefinition = "TEXT")
    private String xmlFirmado;
    @Column(columnDefinition = "TEXT")
    private String xmlOriginal;
    
    // Estado del documento
    @Enumerated(EnumType.STRING)
    private EstadoDE estado;
    private String codigoRespuestaSifen;
    private String mensajeRespuestaSifen;
    
    // Información adicional
    private String numeroDocumento;
    private String tipoDocumento;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaRecepcionSifen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_de_id")
    private LoteDE loteDe;
    
    // Campos de auditoría
    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
