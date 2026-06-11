package com.franco.dev.domain.activos;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tipo_vehiculo", schema = "vehiculos")
public class TipoVehiculo implements Identifiable<Long> {

        private static final long serialVersionUID = 1L;

        @Id
        @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
        @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
        private Long id;

        private String descripcion;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "usuario_id", nullable = true)
        private Usuario usuario;

        @Column(name = "creado_en")
        private LocalDateTime creadoEn;
}
