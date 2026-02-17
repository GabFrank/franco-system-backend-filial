package com.franco.dev.repository.administrativo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MarcacionRepository extends HelperRepository<Marcacion, Long> {

        default Class<Marcacion> getEntityClass() {
                return Marcacion.class;
        }

        Page<Marcacion> findByUsuarioId(Long usuarioId, Pageable pageable);

        @Query("SELECT m FROM Marcacion m WHERE " +
                        "((cast(m.fechaEntrada as date) >= cast(?1 as date) AND cast(m.fechaEntrada as date) <= cast(?2 as date)) "
                        +
                        "OR (cast(m.fechaSalida as date) >= cast(?1 as date) AND cast(m.fechaSalida as date) <= cast(?2 as date)))")
        Page<Marcacion> findByFechaRange(String fechaInicio, String fechaFin, Pageable pageable);

        @Query("SELECT m FROM Marcacion m WHERE m.usuario.id = ?1 " +
                        "AND ((cast(m.fechaEntrada as date) >= cast(?2 as date) AND cast(m.fechaEntrada as date) <= cast(?3 as date)) "
                        +
                        "OR (cast(m.fechaSalida as date) >= cast(?2 as date) AND cast(m.fechaSalida as date) <= cast(?3 as date)))")
        Page<Marcacion> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin,
                        Pageable pageable);

        List<Marcacion> findBySucursalEntradaId(Long sucursalId);

        List<Marcacion> findBySucursalSalidaId(Long sucursalId);

}