package com.franco.dev.domain.personas;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.utilitarios.JsonIdView;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "persona", schema = "personas")
public class Persona implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @JsonView(JsonIdView.Id.class)
    private Long id;
    private String nombre;
    private String apodo;
    private LocalDateTime nacimiento;
    private String documento;
    private String sexo;
    private String direccion;
    private String telefono;
    private String socialMedia;
    private String imagenes;
    private String email;

    @Column(name = "embedding", columnDefinition = "text")
    private String embedding;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ciudad_id", nullable = true)
    @JsonIgnore
    private Ciudad ciudad;

    /**
     * Nulo = Paraguay. Sin FK en la filial: un id que no esta en general.pais se lee como nulo. Solo lectura para
     * Hibernate: se escribe con PersonaRepository.actualizarPais, asi ningun save la pisa con un valor viejo.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pais_id", nullable = true, insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    @JsonIgnore
    private Pais pais;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    @JsonIgnore
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
