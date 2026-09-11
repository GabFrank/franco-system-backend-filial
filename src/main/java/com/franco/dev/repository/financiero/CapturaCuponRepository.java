package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CapturaCuponRepository extends HelperRepository<CapturaCupon, Long> {

    default Class<CapturaCupon> getEntityClass() {
        return CapturaCupon.class;
    }

    Optional<CapturaCupon> findByToken(String token);

    /**
     * Toma la fila con lock pesimista antes de consumirla.
     *
     * <p>Sin esto, dos subidas simultaneas con el mismo token --doble toque, dos pestanhas, un
     * reintento apurado-- pasan las dos el chequeo de "ya se uso" y corren el OCR dos veces,
     * pisandose el resultado. Es el patron que todo servicio que muta estado sigue en este
     * modulo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CapturaCupon c WHERE c.token = :token")
    Optional<CapturaCupon> lockByToken(@Param("token") String token);

    /** Para el job de purga: capturas que nadie uso y ya vencieron. */
    List<CapturaCupon> findByUsadoEnIsNullAndExpiraEnBefore(LocalDateTime limite);

    /** Capturas con foto guardada anteriores al corte. Lo que la purga mira. */
    java.util.List<CapturaCupon> findByCreadoEnBeforeAndImagenUrlIsNotNull(java.time.LocalDateTime corte);
}
