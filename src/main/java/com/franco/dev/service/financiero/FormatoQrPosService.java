package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FormatoQrPos;
import com.franco.dev.repository.financiero.FormatoQrPosRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Solo lectura en filial: las filas llegan del central por MAIN_TO_ALL.
 */
@Service
@AllArgsConstructor
public class FormatoQrPosService extends CrudService<FormatoQrPos, FormatoQrPosRepository> {

    private final FormatoQrPosRepository repository;

    @Override
    public FormatoQrPosRepository getRepository() {
        return repository;
    }

    public List<FormatoQrPos> findActivos() {
        return repository.findByActivoTrueOrderByIdAsc();
    }
}
