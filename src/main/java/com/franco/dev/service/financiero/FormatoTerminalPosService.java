package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.repository.financiero.FormatoTerminalPosRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Solo lectura en filial: las filas llegan del central por MAIN_TO_ALL.
 */
@Service
@AllArgsConstructor
public class FormatoTerminalPosService extends CrudService<FormatoTerminalPos, FormatoTerminalPosRepository> {

    private final FormatoTerminalPosRepository repository;

    @Override
    public FormatoTerminalPosRepository getRepository() {
        return repository;
    }

    /**
     * Los formatos elegibles para asignar a una terminal.
     * <p>
     * Ojo: <b>no</b> es la lista contra la que se resuelve el formato de una venta. Eso es un
     * salto directo por {@code terminal_pos.formato_terminal_pos_id}, y una terminal cuyo formato
     * quedo inactivo sigue funcionando. Ver {@link FormatoTerminalPos#getActivo()}.
     */
    public List<FormatoTerminalPos> findActivos() {
        return repository.findByActivoTrueOrderByIdAsc();
    }
}
