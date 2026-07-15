package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.repository.financiero.TerminalPosRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TerminalPosService extends CrudService<TerminalPos, TerminalPosRepository> {

    private final TerminalPosRepository repository;

    @Override
    public TerminalPosRepository getRepository() {
        return repository;
    }

    public TerminalPos findByCodigo(String codigo) {
        return repository.findByCodigoIgnoreCase(codigo);
    }

    public List<TerminalPos> searchByAll(String texto) {
        texto = texto != null ? texto.toUpperCase() : "";
        return repository.findByAll(texto);
    }

    public Page<TerminalPos> filter(String descripcion, String codigo, Boolean activo, int page, int size) {
        descripcion = (descripcion != null && !descripcion.trim().isEmpty()) ? descripcion.toUpperCase() : null;
        codigo = (codigo != null && !codigo.trim().isEmpty()) ? codigo.toUpperCase() : null;
        return repository.filterTerminalPos(descripcion, codigo, activo, PageRequest.of(page, size));
    }

    @Override
    public TerminalPos save(TerminalPos entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        return super.save(entity);
    }
}
