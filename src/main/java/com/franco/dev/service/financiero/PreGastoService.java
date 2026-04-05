package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.repository.financiero.PreGastoRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class PreGastoService {

    private final PreGastoRepository repository;
    private final Environment env;

    public PreGastoRepository getRepository() {
        return repository;
    }

    public List<PreGasto> findAll() {
        return repository.findAll();
    }

    public Optional<PreGasto> findById(Long id) {
        Long sucursalId = Long.valueOf(env.getProperty("sucursalId"));
        PreGasto pg = repository.findByIdAndSucursalId(id, sucursalId);
        return Optional.ofNullable(pg);
    }

    public List<PreGasto> findByFuncionario(Long funcionarioId) {
        return repository.buscarPorFuncionario(funcionarioId);
    }

    public List<PreGasto> findByEstado(String estado) {
        return repository.buscarPorEstado(estado);
    }

    public List<PreGasto> findByEstadoAndSucursal(String estado, Long sucursalId) {
        return repository.buscarPorEstadoYSucursal(estado, sucursalId);
    }

    public List<PreGasto> findByTexto(String texto, Long sucursalId) {
        return repository.buscarPorTexto(texto, sucursalId);
    }

    public PreGasto save(PreGasto entity) {
        if (entity.getSucursalId() == null) {
            entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        }
        return repository.save(entity);
    }

    public PreGasto saveAndSend(PreGasto entity, Boolean enviar) {
        if (entity.getSucursalId() == null) {
            entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        }
        if (entity.getId() == null) {
            Long sucursalId = Long.valueOf(env.getProperty("sucursalId"));
            Long lastId = repository.findMaxId(sucursalId);
            entity.setId(lastId == null ? 1 : lastId + 1);
        }
        // In this project it is simplified, so we just save
        return repository.save(entity);
    }

    public Boolean deleteById(Long id) {
        try {
            Long sucursalId = Long.valueOf(env.getProperty("sucursalId"));
            PreGasto pg = repository.findByIdAndSucursalId(id, sucursalId);
            if(pg != null) {
                repository.delete(pg);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
