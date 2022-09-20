package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.repository.productos.PrecioPorSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PrecioPorSucursalService extends CrudService<PrecioPorSucursal, PrecioPorSucursalRepository> {

    @Autowired
    private final PrecioPorSucursalRepository repository;

    @Override
    public PrecioPorSucursalRepository getRepository() {
        return repository;
    }

    public List<PrecioPorSucursal> findByPresentacionId(Long id){
        return  repository.findByPresentacionId(id);
    }
    public List<PrecioPorSucursal> findBySucursalId(Long id){
        return  repository.findBySucursalId(id);
    }

    @Override
    public PrecioPorSucursal save(PrecioPorSucursal entity) {
        PrecioPorSucursal p = super.save(entity);
        return p;
    }

    public PrecioPorSucursal findPrincipalByPrecionacionId(Long id){
        return repository.findPrincipalByPresentacionId(id);
    }
}
