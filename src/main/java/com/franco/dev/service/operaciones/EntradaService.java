package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Entrada;
import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.print.operaciones.MovimientoPrintService;
import com.franco.dev.repository.operaciones.EntradaRepository;
import com.franco.dev.repository.operaciones.VentaItemRepository;
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
public class EntradaService extends CrudService<Entrada, EntradaRepository> {

    private final Logger log = LoggerFactory.getLogger(EntradaService.class);

    private final EntradaRepository repository;

    @Autowired
    private EntradaItemService entradaItemService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Override
    public EntradaRepository getRepository() {
        return repository;
    }

    @Autowired
    private MovimientoPrintService movimientoPrintService;

    @Override
    public Entrada save(Entrada entity) {
        if(entity.getCreadoEn()!=null){
            log.warn(entity.getCreadoEn().toString());
        }
        if(entity.getCreadoEn()==null) entity.setCreadoEn(LocalDateTime.now());
        Entrada e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public List<Entrada> findByDate(String inicio, String fin){
        return repository.findByDate(inicio, fin);
    }

    @Transactional
    public Boolean finalizarEntrada(Long id){
        Boolean ok = true;
        Entrada entrada = findById(id).orElse(null);
        List<EntradaItem> entradaItemList = new ArrayList<>();
        if(entrada!=null){
            entradaItemList = entradaItemService.findByEntradaId(entrada.getId());
            if(!entradaItemList.isEmpty()){
                for(EntradaItem e: entradaItemList){
                    MovimientoStock movimientoStock = new MovimientoStock();
                    movimientoStock.setCantidad(e.getCantidad() * e.getPresentacion().getCantidad());
                    movimientoStock.setProducto(e.getProducto());
                    movimientoStock.setEstado(true);
                    movimientoStock.setReferencia(id);
                    movimientoStock.setTipoMovimiento(TipoMovimiento.ENTRADA);
                    movimientoStock.setCreadoEn(LocalDateTime.now());
                    movimientoStock.setUsuario(e.getUsuario());
                    MovimientoStock newMovimiento = movimientoStockService.save(movimientoStock);
                    if(newMovimiento.getId()==null){
                        ok = false;
                    }
                }
            }
            entrada.setActivo(ok);
            save(entrada);
        }
        return ok;
    }

    public Boolean imprimirEntrada(Long id){
        Entrada entrada = findById(id).orElse(null);
        if(entrada!=null){
            List<EntradaItem> entradaItemList = entradaItemService.findByEntradaId(id);
            if(!entradaItemList.isEmpty()){
                return movimientoPrintService.entrada58mm(entrada, entradaItemList, "TICKET1");
            }
        }
        return false;
    }
}