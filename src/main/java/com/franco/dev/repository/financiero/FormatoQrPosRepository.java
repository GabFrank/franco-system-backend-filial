package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FormatoQrPos;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface FormatoQrPosRepository extends HelperRepository<FormatoQrPos, Long> {

    default Class<FormatoQrPos> getEntityClass() {
        return FormatoQrPos.class;
    }

    List<FormatoQrPos> findByActivoTrueOrderByIdAsc();
}
