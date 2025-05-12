package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface PdvCajaRepository extends HelperRepository<PdvCaja, Long> {

    default Class<PdvCaja> getEntityClass() {
        return PdvCaja.class;
    }

    List<PdvCaja> findByUsuarioIdAndActivo(Long id, Boolean activo);

    Optional<PdvCaja> findById(Long id);

    public List<PdvCaja> findByCreadoEnBetween(LocalDateTime inicio, LocalDateTime fin);

    Optional<PdvCaja> findFirstByMaletinIdOrderByCreadoEnDesc(Long id);

    @Query(value = "select c from PdvCaja c " +
            "join c.maletin m " +
            "join c.usuario u " +
            "where c.sucursalId = :sucId and " +
            "(:cajaId is null or c.id = :cajaId) and " +
            "(:maletinId is null or m.id = :maletinId) and " +
            "(:cajeroId is null or u.id = :cajeroId) and " +
            "(:verificado is null or c.verificado = :verificado) and " +
            "((:cajaId is not null) or (cast(:fechaInicio as timestamp) is null or cast(:fechaFin as timestamp) is null) or c.creadoEn between :fechaInicio and :fechaFin) and " +
            "(c.estado = :estado or cast(:estado as com.franco.dev.domain.financiero.enums.PdvCajaEstado) is null) order by c.id")
    public Page<PdvCaja> findAllWithFilters(Long cajaId, PdvCajaEstado estado, Long maletinId, Long cajeroId, LocalDateTime fechaInicio, LocalDateTime fechaFin, Long sucId, Boolean verificado, Pageable pageable);

    /**
     * Finds the maximum ID value in the PdvCaja table
     * @return The maximum ID value or null if table is empty
     */
    @Query("SELECT MAX(c.id) FROM PdvCaja c")
    Long findMaxId();

}