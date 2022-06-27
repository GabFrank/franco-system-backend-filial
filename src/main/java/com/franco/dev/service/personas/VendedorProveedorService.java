package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.domain.personas.VendedorProveedor;
import com.franco.dev.repository.personas.VendedorProveedorRepository;
import com.franco.dev.repository.personas.VendedorRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class VendedorProveedorService extends CrudService<VendedorProveedor, VendedorProveedorRepository> {

    private final VendedorProveedorRepository repository;

    @Override
    public VendedorProveedorRepository getRepository() {
        return repository;
    }

    public List<Vendedor> findByProveedorId(Long id){
        List<VendedorProveedor> vp = repository.findDistinctByProveedorId(id);
        List<Vendedor> v = new ArrayList<>();
        for(VendedorProveedor e: vp){
            v.add(e.getVendedor());
        }
        return v;
    }
    public List<Proveedor> findByVendedorId(Long id){
        List<VendedorProveedor> vp = repository.findDistinctByProveedorId(id);
        List<Proveedor> v = new ArrayList<>();
        for(VendedorProveedor e: vp){
            v.add(e.getProveedor());
        }
        return v;
    }
}
