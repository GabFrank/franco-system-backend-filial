package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EventoCancelacionDE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventoCancelacionDERepository extends JpaRepository<EventoCancelacionDE, Long> {
    
    /**
     * Busca todos los eventos de cancelación de un documento electrónico.
     */
    List<EventoCancelacionDE> findByDocumentoElectronicoId(Long documentoElectronicoId);
    
    /**
     * Busca todos los eventos activos de un documento, ordenados por fecha de creación descendente.
     */
    @Query("SELECT e FROM EventoCancelacionDE e WHERE e.documentoElectronico.id = :documentoId AND e.activo = true ORDER BY e.creadoEn DESC")
    List<EventoCancelacionDE> findActivosByDocumentoElectronicoId(@Param("documentoId") Long documentoId);
    
    /**
     * Busca eventos activos por CDC del documento, ordenados por fecha descendente.
     */
    @Query("SELECT e FROM EventoCancelacionDE e WHERE e.cdcDocumento = :cdcDocumento AND e.activo = true ORDER BY e.creadoEn DESC")
    List<EventoCancelacionDE> findActivosByCdcDocumento(@Param("cdcDocumento") String cdcDocumento);
    
    /**
     * Busca el evento de cancelación más reciente para un documento (activo o no).
     */
    @Query("SELECT e FROM EventoCancelacionDE e WHERE e.documentoElectronico.id = :documentoId ORDER BY e.creadoEn DESC")
    List<EventoCancelacionDE> findByDocumentoElectronicoIdOrderByCreadoEnDesc(@Param("documentoId") Long documentoId);
    
    /**
     * Busca un evento de cancelación por CDC del documento (primer resultado).
     */
    Optional<EventoCancelacionDE> findFirstByCdcDocumentoOrderByCreadoEnDesc(String cdcDocumento);
    
    /**
     * Busca un evento por su ID único.
     */
    Optional<EventoCancelacionDE> findByEventoId(String eventoId);
    
    /**
     * Verifica si un documento tiene eventos de cancelación aprobados.
     */
    @Query("SELECT COUNT(e) > 0 FROM EventoCancelacionDE e WHERE e.documentoElectronico.id = :documentoId AND e.estado = 'APROBADO'")
    boolean existeCancelacionAprobada(@Param("documentoId") Long documentoId);
    
    /**
     * Busca eventos que necesitan reintento (ERROR_ENVIO o RECHAZADO, activos).
     */
    @Query("SELECT e FROM EventoCancelacionDE e WHERE e.activo = true AND (e.estado = 'ERROR_ENVIO' OR e.estado = 'RECHAZADO') ORDER BY e.creadoEn DESC")
    List<EventoCancelacionDE> findEventosFallidos();
}

