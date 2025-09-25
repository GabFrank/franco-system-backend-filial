package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lote_de", schema = "financiero")
public class LoteDE implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private EstadoLoteDE estado;

    private LocalDateTime fechaProcesado;
    private LocalDateTime fechaUltimoIntento;
    private Integer intentos;

    @Column(columnDefinition = "TEXT")
    private String respuestaSifen;
    private String protocolo;

    @OneToMany(mappedBy = "loteDe", fetch = FetchType.LAZY)
    private List<DocumentoElectronico> documentosElectronicos;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
