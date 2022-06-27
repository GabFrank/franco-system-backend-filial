package com.franco.dev.service.restaurant;

import com.franco.dev.domain.productos.Combo;
import com.franco.dev.domain.productos.ComboItem;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.ProductoIngrediente;
import com.franco.dev.domain.restaurant.PedidoItemIngredienteRes;
import com.franco.dev.domain.restaurant.PedidoItemRes;
import com.franco.dev.domain.restaurant.PedidoRes;
import com.franco.dev.repository.restaurant.PedidoItemResRepository;
import com.franco.dev.repository.restaurant.PedidoResRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.ComboItemService;
import com.franco.dev.service.productos.ComboService;
import com.franco.dev.service.productos.IngredienteService;
import com.franco.dev.service.productos.ProductoIngredienteService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PedidoItemResService extends CrudService<PedidoItemRes, PedidoItemResRepository> {

    @Autowired
    private final PedidoItemResRepository repository;
//    private final PersonaPublisher personaPublisher;

    @Autowired
    private final PedidoItemIngredienteResService pedidoItemIngredienteResService;

    @Autowired
    private final ComboService comboService;

    @Autowired
    private final ComboItemService comboItemService;

    @Autowired
    private final ProductoIngredienteService productoIngredienteService;


    @Override
    public PedidoItemResRepository getRepository() {
        return repository;
    }

    @Override
    public PedidoItemRes save(PedidoItemRes entity) {
        PedidoItemRes p = super.save(entity);
        if(p.getProducto().getCombo()){
            Combo combo = comboService.findByProductoId(p.getProducto().getId());
            List<ComboItem> comboItems = comboItemService.findByCombo(combo.getId());
            for(ComboItem ci: comboItems){
                if(ci.getProducto().getIngrediente()==true){
                    List<ProductoIngrediente> piList = productoIngredienteService.findByProducto(ci.getProducto().getId());
                    for(ProductoIngrediente pi: piList){
                        PedidoItemIngredienteRes pedidoItemIngredienteRes = new PedidoItemIngredienteRes();
                        pedidoItemIngredienteRes.setProductoIngrediente(pi);
                        pedidoItemIngredienteRes.setPedidoItemRes(entity);
                        if(!pi.getAdicional()){
                            pedidoItemIngredienteRes.setAdicionar(true);
                        } else {
                            pedidoItemIngredienteRes.setAdicionar(false);
                        }
                        pedidoItemIngredienteRes.setCosto(pi.getExtra());
                        pedidoItemIngredienteRes.setPrecio(pi.getPrecio());
                        pedidoItemIngredienteResService.save(pedidoItemIngredienteRes);
                    }
                }
            }
        }
        return p;
    }

    public List<PedidoItemRes> findByPedidoResId(Long id){ return repository.findByPedidoResId(id);}

}
