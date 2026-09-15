package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPosRegion;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

/**
 * Solo lectura: las regiones llegan del central por MAIN_TO_ALL y el ABM vive alla.
 */
public interface FormatoTerminalPosRegionRepository extends HelperRepository<FormatoTerminalPosRegion, Long> {

    default Class<FormatoTerminalPosRegion> getEntityClass() {
        return FormatoTerminalPosRegion.class;
    }

    /**
     * El mapa de un formato, en el orden en que se declararon los campos.
     * <p>
     * Se navega {@code formatoTerminalPos.id} y no una columna suelta: Hibernate 5 no genera JOIN
     * para el {@code .id} de un {@code @ManyToOne}, lee la FK directo. Verificado en este repo.
     */
    List<FormatoTerminalPosRegion> findByFormatoTerminalPos_IdOrderByOrdenAscIdAsc(Long formatoTerminalPosId);
}
