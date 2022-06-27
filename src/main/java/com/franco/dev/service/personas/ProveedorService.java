package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.repository.personas.ProveedorRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ProveedorService extends CrudService<Proveedor, ProveedorRepository> {

    private final ProveedorRepository repository;


    @Override
    public ProveedorRepository getRepository() {
        return repository;
    }

    public Proveedor findByPersonaId(Long id){
        return repository.findByPersonaId(id);
    }

    public List<Proveedor> findByPersonaNombre(String texto){
        return repository.findByPersona(texto.toUpperCase());
    }

    public List<Proveedor> findByVendedorId(Long id){ return repository.findByVendedorId(id); }

}
