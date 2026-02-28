package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JornadaRepository extends HelperRepository<Jornada, EmbebedPrimaryKey> {

    default Class<Jornada> getEntityClass() {
        return Jornada.class;
    }

    @Query("SELECT MAX(m.id) FROM Jornada m WHERE m.sucursalId = :sucursalId")
    Long findMaxId(@Param("sucursalId") Long sucursalId);

    List<Jornada> findByUsuarioId(Long usuarioId);

    @Query("SELECT j FROM Jornada j WHERE j.usuario.id = ?1 " +
            "AND cast(j.fecha as date) >= cast(?2 as date) AND cast(j.fecha as date) <= cast(?3 as date) ORDER BY j.id DESC")
    List<Jornada> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin);

    @Query("SELECT j FROM Jornada j WHERE cast(j.fecha as date) >= cast(?1 as date) AND cast(j.fecha as date) <= cast(?2 as date) ORDER BY j.id DESC")
    List<Jornada> findByFechaRange(String fechaInicio, String fechaFin);

    @Query("SELECT j FROM Jornada j WHERE j.usuario.id = ?1 AND cast(j.fecha as date) = cast(?2 as date) ORDER BY j.id ASC")
    List<Jornada> findByUsuarioIdAndFecha(Long usuarioId, String fecha);

    Optional<Jornada> findByMarcacionEntradaId(Long id);

}
