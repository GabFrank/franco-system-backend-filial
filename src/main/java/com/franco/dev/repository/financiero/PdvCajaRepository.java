package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PdvCajaRepository extends HelperRepository<PdvCaja, Long> {

    default Class<PdvCaja> getEntityClass() {
        return PdvCaja.class;
    }

    List<PdvCaja> findByUsuarioIdAndActivo(Long id, Boolean activo);

    Optional<PdvCaja> findById(Long id);

    public List<PdvCaja> findByCreadoEnBetween(LocalDateTime inicio, LocalDateTime fin);

    Optional<PdvCaja> findFirstByMaletinIdOrderByCreadoEnDesc(Long id);

}