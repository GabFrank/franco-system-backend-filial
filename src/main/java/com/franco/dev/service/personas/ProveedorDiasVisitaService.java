package com.franco.dev.service.personas;

import com.franco.dev.domain.general.enums.DiasSemana;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.ProveedorDiasVisita;
import com.franco.dev.repository.personas.ProveedorDiasVisitaRepository;
import com.franco.dev.repository.personas.ProveedorRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ProveedorDiasVisitaService extends CrudService<ProveedorDiasVisita, ProveedorDiasVisitaRepository> {

    private final ProveedorDiasVisitaRepository repository;


    @Override
    public ProveedorDiasVisitaRepository getRepository() {
        return repository;
    }

    public List<DiasSemana> findByProveedorId(Long id){
        List<ProveedorDiasVisita> pdv = repository.findByProveedorId(id);
        List<DiasSemana> ds = new ArrayList<>();
        for(ProveedorDiasVisita e: pdv){
            ds.add(e.getDia());
        }
        return ds;
    }

//    public List<ProveedorDiasVisita> findByProveedorNombre(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByProveedorPersonaNombre(texto.toUpperCase());
//    }

    public List<Proveedor> findByDia(DiasSemana dia){
        List<ProveedorDiasVisita> pdv = repository.findByDia(dia);
        List<Proveedor> pro = new ArrayList<>();
        for(ProveedorDiasVisita e: pdv){
            pro.add(e.getProveedor());
        }
        return pro;
    }

}
