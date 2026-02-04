package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface JornadaRepository extends HelperRepository<Jornada, Long> {

    default Class<Jornada> getEntityClass() {
        return Jornada.class;
    }

    List<Jornada> findByUsuarioId(Long usuarioId);

    @Query("SELECT j FROM Jornada j WHERE j.usuario.id = ?1 " +
            "AND cast(j.fecha as date) >= cast(?2 as date) AND cast(j.fecha as date) <= cast(?3 as date)")
    List<Jornada> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin);

    @Query("SELECT j FROM Jornada j WHERE j.usuario.id = ?1 AND cast(j.fecha as date) = cast(?2 as date)")
    Optional<Jornada> findByUsuarioIdAndFecha(Long usuarioId, String fecha);

}
