package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.repository.operaciones.EntradaItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class EntradaItemService extends CrudService<EntradaItem, EntradaItemRepository> {
    private final EntradaItemRepository repository;

    @Autowired
    MovimientoStockService movimientoStockService;

    @Override
    public EntradaItemRepository getRepository() {
        return repository;
    }

//    @Resource
//    UserTransaction tran;

//    public List<EntradaItem> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//
    public List<EntradaItem> findByEntradaId(Long id){
        return repository.findByEntradaId(id);
    }

//    @SneakyThrows
    @Override
    public EntradaItem save(EntradaItem entity) {
        EntradaItem e = new EntradaItem();
        if(entity.getCreadoEn()==null) entity.setCreadoEn(LocalDateTime.now());
        e = super.save(entity);
        return e;
    }
}