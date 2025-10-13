package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EventoNominacionDE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventoNominacionDERepository extends JpaRepository<EventoNominacionDE, Long> {
    
    /**
     * Busca todos los eventos de nominación de un documento electrónico.
     */
    List<EventoNominacionDE> findByDocumentoElectronicoId(Long documentoElectronicoId);
    
    /**
     * Busca todos los eventos activos de un documento, ordenados por fecha de creación descendente.
     */
    @Query("SELECT e FROM EventoNominacionDE e WHERE e.documentoElectronico.id = :documentoId AND e.activo = true ORDER BY e.creadoEn DESC")
    List<EventoNominacionDE> findActivosByDocumentoElectronicoId(@Param("documentoId") Long documentoId);
    
    /**
     * Busca eventos activos por CDC del documento, ordenados por fecha descendente.
     */
    @Query("SELECT e FROM EventoNominacionDE e WHERE e.cdcDocumento = :cdcDocumento AND e.activo = true ORDER BY e.creadoEn DESC")
    List<EventoNominacionDE> findActivosByCdcDocumento(@Param("cdcDocumento") String cdcDocumento);
    
    /**
     * Busca un evento por su ID único.
     */
    Optional<EventoNominacionDE> findByEventoId(String eventoId);
    
    /**
     * Verifica si un documento tiene eventos de nominación aprobados.
     */
    @Query("SELECT COUNT(e) > 0 FROM EventoNominacionDE e WHERE e.documentoElectronico.id = :documentoId AND e.estado = 'APROBADO'")
    boolean existeNominacionAprobada(@Param("documentoId") Long documentoId);
}

