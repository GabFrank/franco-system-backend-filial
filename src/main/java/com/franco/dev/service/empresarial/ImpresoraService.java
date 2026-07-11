package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.repository.empresarial.ImpresoraRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ImpresoraService extends CrudService<Impresora, ImpresoraRepository> {

    private final ImpresoraRepository repository;

    @Override
    public ImpresoraRepository getRepository() {
        return repository;
    }

    public List<Impresora> findBySucursalId(Long id) {
        return repository.findBySucursalId(id);
    }

    public List<Impresora> findActivas() {
        return repository.findByActivoTrueOrderByIdAsc();
    }
}
