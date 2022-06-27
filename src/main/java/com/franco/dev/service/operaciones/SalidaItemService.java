package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.SalidaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.operaciones.EntradaItemRepository;
import com.franco.dev.repository.operaciones.SalidaItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.transaction.UserTransaction;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class SalidaItemService extends CrudService<SalidaItem, SalidaItemRepository> {
    private final SalidaItemRepository repository;

    @Override
    public SalidaItemRepository getRepository() {
        return repository;
    }

//    @Resource
//    UserTransaction tran;

//    public List<SalidaItem> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//
    public List<SalidaItem> findBySalidaId(Long id){
        return repository.findBySalidaId(id);
    }

//    @SneakyThrows
    @Override
    public SalidaItem save(SalidaItem entity) {
        Boolean isNew = entity.getId()==null;
        SalidaItem e = new SalidaItem();
        if(isNew) entity.setCreadoEn(LocalDateTime.now());
        try {
//            tran.begin();
            e = super.save(entity);
            MovimientoStock movimientoStock = new MovimientoStock();
            movimientoStock.setCantidad(entity.getCantidad());
            movimientoStock.setProducto(entity.getProducto());
            movimientoStock.setEstado(true);
            movimientoStock.setReferencia(entity.getId());
            movimientoStock.setTipoMovimiento(TipoMovimiento.SALIDA);
            movimientoStock.setCreadoEn(LocalDateTime.now());
            movimientoStock.setUsuario(entity.getUsuario());
//            tran.commit();
        } catch (Exception err) {
            // Error occurred, rollback transaction
//            tran.rollback();
        }
//        personaPublisher.publish(p);
        return e;
    }
}