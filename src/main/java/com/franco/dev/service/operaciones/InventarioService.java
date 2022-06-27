package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Entrada;
import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.print.operaciones.MovimientoPrintService;
import com.franco.dev.repository.operaciones.EntradaRepository;
import com.franco.dev.repository.operaciones.InventarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class InventarioService extends CrudService<Inventario, InventarioRepository> {

    private final Logger log = LoggerFactory.getLogger(InventarioService.class);

    private final InventarioRepository repository;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Override
    public InventarioRepository getRepository() {
        return repository;
    }

    @Autowired
    private MovimientoPrintService movimientoPrintService;

    @Override
    public Inventario save(Inventario entity) {
        if(entity.getFechaInicio()==null) entity.setFechaInicio(LocalDateTime.now());
        Inventario e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public List<Inventario> findByDate(String inicio, String fin){
        return repository.findByDate(inicio, fin);
    }
    public List<Inventario> findByUsuario(Long id){
        return repository.findByUsuarioId(id);
    }
//    @Transactional
//    public Boolean finalizarInventario(Long id){
//        Boolean ok = true;
//        Inventario entrada = findById(id).orElse(null);
//        List<InventarioItem> entradaItemList = new ArrayList<>();
//        if(entrada!=null){
//            entradaItemList = entradaItemService.findByInventarioId(entrada.getId());
//            if(!entradaItemList.isEmpty()){
//                for(InventarioItem e: entradaItemList){
//                    MovimientoStock movimientoStock = new MovimientoStock();
//                    movimientoStock.setCantidad(e.getCantidad() * e.getPresentacion().getCantidad());
//                    movimientoStock.setProducto(e.getProducto());
//                    movimientoStock.setEstado(true);
//                    movimientoStock.setReferencia(id);
//                    movimientoStock.setTipoMovimiento(TipoMovimiento.ENTRADA);
//                    movimientoStock.setCreadoEn(LocalDateTime.now());
//                    movimientoStock.setUsuario(e.getUsuario());
//                    MovimientoStock newMovimiento = movimientoStockService.save(movimientoStock);
//                    if(newMovimiento.getId()==null){
//                        ok = false;
//                    }
//                }
//            }
//            entrada.setActivo(ok);
//            save(entrada);
//        }
//        return ok;
//    }

//    public Boolean imprimirInventario(Long id){
//        Inventario entrada = findById(id).orElse(null);
//        if(entrada!=null){
//            List<InventarioItem> entradaItemList = entradaItemService.findByInventarioId(id);
//            if(!entradaItemList.isEmpty()){
//                return movimientoPrintService.entrada58mm(entrada, entradaItemList, "TICKET1");
//            }
//        }
//        return false;
//    }
}