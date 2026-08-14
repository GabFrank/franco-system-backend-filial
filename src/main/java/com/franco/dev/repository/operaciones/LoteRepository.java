package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Maestro de lotes. SOLO LECTURA en la filial: las filas bajan del central por replicación
 * MAIN_TO_ALL. No agregar métodos de escritura acá.
 */
@Repository
public interface LoteRepository extends HelperRepository<Lote, Long> {

    default Class<Lote> getEntityClass() {
        return Lote.class;
    }

    Optional<Lote> findByProductoIdAndNumeroLote(Long productoId, String numeroLote);

    List<Lote> findByProductoId(Long productoId);
}
