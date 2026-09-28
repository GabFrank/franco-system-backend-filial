package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.repository.financiero.TerminalPosRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class TerminalPosService extends CrudService<TerminalPos, TerminalPosRepository> {

    private final TerminalPosRepository repository;

    @Override
    public TerminalPosRepository getRepository() {
        return repository;
    }

    /**
     * Las terminales activas con exactamente esta serie. Devuelve la lista y no una sola porque
     * quien llama tiene que poder distinguir "ninguna" de "mas de una": ante ambiguedad no se
     * elige, se pregunta.
     */
    public List<TerminalPos> findPorSerie(String serie) {
        String s = serie == null ? null : serie.trim();
        if (s == null || s.isEmpty()) return new ArrayList<TerminalPos>();
        return repository.findBySerieIgnoreCaseAndActivoTrue(s);
    }

    public TerminalPos findByCodigo(String codigo) {
        return repository.findByCodigoIgnoreCase(codigo);
    }

    public List<TerminalPos> searchByAll(String texto) {
        texto = texto != null ? texto.toUpperCase() : "";
        return repository.findByAll(texto);
    }

    public Page<TerminalPos> filter(String descripcion, String codigo, String serie, Long sucursalId,
                                    Boolean activo, int page, int size) {
        descripcion = (descripcion != null && !descripcion.trim().isEmpty()) ? descripcion.toUpperCase() : null;
        codigo = (codigo != null && !codigo.trim().isEmpty()) ? codigo.toUpperCase() : null;
        serie = (serie != null && !serie.trim().isEmpty()) ? serie.trim().toUpperCase() : null;
        return repository.filterTerminalPos(descripcion, codigo, serie, sucursalId,
                activo, PageRequest.of(page, size));
    }

    @Override
    public TerminalPos save(TerminalPos entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        return super.save(entity);
    }
}
