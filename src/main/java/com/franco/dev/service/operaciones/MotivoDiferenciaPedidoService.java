package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MotivoDiferenciaPedido;
import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.repository.operaciones.MotivoDiferenciaPedidoRepository;
import com.franco.dev.repository.operaciones.NotaPedidoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MotivoDiferenciaPedidoService extends CrudService<MotivoDiferenciaPedido, MotivoDiferenciaPedidoRepository> {
    private final MotivoDiferenciaPedidoRepository repository;

    @Override
    public MotivoDiferenciaPedidoRepository getRepository() {
        return repository;
    }

//    public List<MotivoDiferenciaPedido> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//    }

    @Override
    public MotivoDiferenciaPedido save(MotivoDiferenciaPedido entity) {
        MotivoDiferenciaPedido e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}