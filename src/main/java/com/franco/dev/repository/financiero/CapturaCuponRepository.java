package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CapturaCuponRepository extends HelperRepository<CapturaCupon, Long> {

    default Class<CapturaCupon> getEntityClass() {
        return CapturaCupon.class;
    }

    Optional<CapturaCupon> findByToken(String token);

    /** Para el job de purga: capturas que nadie uso y ya vencieron. */
    List<CapturaCupon> findByUsadoEnIsNullAndExpiraEnBefore(LocalDateTime limite);
}
