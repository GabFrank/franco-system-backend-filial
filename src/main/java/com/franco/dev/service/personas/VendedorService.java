package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.repository.personas.ProveedorRepository;
import com.franco.dev.repository.personas.VendedorRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class VendedorService extends CrudService<Vendedor, VendedorRepository> {

    private final VendedorRepository repository;


    @Override
    public VendedorRepository getRepository() {
        return repository;
    }

    public Vendedor findByPersonaId(Long id){
        return repository.findByPersonaId(id);
    }

    public List<Vendedor> findByPersonaNombre(String texto) {
        return repository.findByPersona(texto.toUpperCase());}
}
