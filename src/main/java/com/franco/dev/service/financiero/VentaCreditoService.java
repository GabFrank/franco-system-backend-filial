package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaCreditoService extends CrudService<VentaCredito, VentaCreditoRepository> {

    private final VentaCreditoRepository repository;

    @Override
    public VentaCreditoRepository getRepository() {
        return repository;
    }

//    public List<VentaCredito> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<VentaCredito> findByClienteAndVencimiento(Long id, LocalDateTime inicio, LocalDateTime fin) {
        return repository.findAllByClienteIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByIdAsc(id, inicio, fin);
    }

    @Override
    public VentaCredito save(VentaCredito entity) {
        VentaCredito e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}