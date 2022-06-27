package com.franco.dev.service.general;

import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.repository.general.ContactoRepository;
import com.franco.dev.repository.general.PaisRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ContactoService extends CrudService<Contacto, ContactoRepository> {

    private final ContactoRepository repository;

    @Override
    public ContactoRepository getRepository() {
        return repository;
    }

    public List<Contacto> findByTelefonoOrNombre(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findbyTelefonoOrPersonaNombre(texto);
    }

    public List<Contacto> findByPersonaId(Long id){
        return  repository.findByPersonaId(id);
    }

    @Override
    public Contacto save(Contacto entity) {
        Contacto e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}