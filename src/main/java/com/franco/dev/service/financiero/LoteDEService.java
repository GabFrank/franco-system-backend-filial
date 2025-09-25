package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.repository.financiero.LoteDERepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class LoteDEService extends CrudService<LoteDE, LoteDERepository> {

    private final LoteDERepository repository;

    @Override
    public LoteDERepository getRepository() {
        return repository;
    }
}
