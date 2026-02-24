package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class FuncionarioService extends CrudService<Funcionario, FuncionarioRepository> {

    private final FuncionarioRepository repository;

    @Override
    public FuncionarioRepository getRepository() {
        return repository;
    }

    public Funcionario findByPersonaId(Long id) {
        return repository.findByPersonaId(id);
    }

    public Funcionario findByUsuarioId(Long id) {
        return repository.findByUsuarioId(id);
    }

    public List<Funcionario> findByPersonaNombre(String texto) {
        texto = texto != null ? texto.replace(" ", "%").toUpperCase() : "";
        List<Funcionario> aux = repository.findByIdOrPersonaNombre(texto);
        return aux;
    }

    @Override
    public Funcionario save(Funcionario entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setActivo(true);
        }
        Funcionario e = repository.save(entity);
        return e;
    }
}
