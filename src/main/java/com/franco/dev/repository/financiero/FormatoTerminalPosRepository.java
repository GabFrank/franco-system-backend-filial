package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface FormatoTerminalPosRepository extends HelperRepository<FormatoTerminalPos, Long> {

    default Class<FormatoTerminalPos> getEntityClass() {
        return FormatoTerminalPos.class;
    }

    List<FormatoTerminalPos> findByActivoTrueOrderByIdAsc();
}
