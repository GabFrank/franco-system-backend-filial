package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.repository.financiero.VentaCreditoCuotaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class VentaCreditoCuotaService extends CrudService<VentaCreditoCuota, VentaCreditoCuotaRepository> {

    private final VentaCreditoCuotaRepository repository;

    @Override
    public VentaCreditoCuotaRepository getRepository() {
        return repository;
    }

//    public List<VentaCreditoCuota> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<VentaCreditoCuota> findByVentaCreditoId(Long id) {
        return repository.findAllByVentaCreditoId(id);
    }

    @Override
    public VentaCreditoCuota save(VentaCreditoCuota entity) {
        VentaCreditoCuota e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}