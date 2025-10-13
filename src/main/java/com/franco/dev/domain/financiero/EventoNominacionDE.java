package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa un evento de nominación de receptor de un Documento Electrónico en SIFEN.
 * 
 * La nominación se usa para identificar al receptor real de una factura que fue emitida como innominada.
 * Solo aplica para facturas electrónicas emitidas sin identificar al receptor (innominadas).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(
        name = "estado_evento_enum",
        typeClass = PostgreSQLEnumType.class
)
@Entity
@Table(name = "evento_nominacion_de", schema = "financiero")
public class EventoNominacionDE implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con el documento electrónico nominado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_electronico_id", nullable = false)
    private DocumentoElectronico documentoElectronico;

    // Datos del evento de nominación enviado
    private String eventoId;           // ID único del evento
    private LocalDateTime fechaFirma;   // Fecha de firma del evento
    private String cdcDocumento;        // CDC del documento que se está nominando
    
    // Datos del receptor nominado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;            // Cliente nominado como receptor
    
    private String nombreReceptor;      // Nombre del receptor nominado
    private String documentoReceptor;   // Documento del receptor nominado
    private String tipoReceptor;        // CONTRIBUYENTE / NO_CONTRIBUYENTE
    
    private BigDecimal totalFactura;    // Total de la factura nominada
    private LocalDateTime fechaEmision; // Fecha de emisión de la factura
    private LocalDateTime fechaRecepcion; // Fecha de recepción por el receptor
    
    @Column(columnDefinition = "TEXT")
    private String xmlEvento;           // XML del evento enviado

    // Respuesta de SIFEN
    @Enumerated(EnumType.STRING)
    @Type(type = "estado_evento_enum")
    @Column(columnDefinition = "financiero.estado_evento_enum")
    private EstadoEvento estado;        // PENDIENTE, APROBADO, RECHAZADO
    
    private LocalDateTime fechaProcesamiento; // Fecha de procesamiento por SIFEN
    private String protocoloAutorizacion;     // Protocolo de autorización de SIFEN
    private String codigoRespuesta;           // Código de respuesta (ej: 0600)
    private String mensajeRespuesta;          // Mensaje de respuesta
    
    @Column(columnDefinition = "TEXT")
    private String respuestaBruta;      // Respuesta completa de SIFEN (XML)

    // Auditoría
    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}

