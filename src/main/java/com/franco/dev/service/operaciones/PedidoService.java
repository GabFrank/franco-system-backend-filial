package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.repository.operaciones.PedidoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.hibernate.Session;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class PedidoService extends CrudService<Pedido, PedidoRepository> {
    private final PedidoRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public PedidoRepository getRepository() {
        return repository;
    }

    public List<Pedido> filterPedidos(String estado,Long sucursalId,String inicio,String fin,Long proveedorId,Long vendedorId,String formaPago,Long productoId){
        if(inicio==null){
            inicio = "2000-01-01";
        }
        if(fin==null){
            fin = "2050-01-01";
        }
        return repository.filterPedidos(estado, sucursalId, inicio, fin, proveedorId, vendedorId, formaPago, productoId);
    }

//    public List<Pedido> filterPedidos(@Param("estado") String estado,@Param("sucursal_id") Long sucursalId,@Param("inicio") String inicio,@Param("fin") String fin,@Param("proveedor_id") Long proveedorId,@Param("vendedor_d") Long vendedorId,@Param("forma_pago") String formaPago,@Param("producto_id") Long productoId){
//        List<Pedido> pedidos = new ArrayList<>();
//        StringBuilder strQuery = new StringBuilder();
//               strQuery.append("select  " +
//                "distinct p.id,  " +
//                "p.moneda_id, " +
//                "p.proveedor_id,  " +
//                "p.vendedor_id,  " +
//                "p.forma_pago,  " +
//                "p.dias_cheque,  " +
//                "p.fecha_de_entrega,  " +
//                "p.usuario_id, " +
//                "p.cantidad_notas, " +
//                "p.descuento , " +
//                "p.necesidad_id  " +
//                "from operaciones.pedido p " +
//                "join operaciones.pedido_item pi2 on pi2.pedido_id = p.id " +
//                "join operaciones.pedido_item_sucursal pis on pis.pedido_item_id = pi2.id where 1=1 ");
//        if(estado!=null){
//            strQuery.append("and cast(p.estado as text) = cast(:estado as text) ");
//        }
//        if(sucursalId!=null){
//            strQuery.append("and pis.sucursal_id = :sucursal_id ");
//        }
//        if(inicio!=null){
//            strQuery.append("and cast(p.creado_en as Date) between cast(:inicio as Date) and cast(:fin as Date) ");
//        }
//        if(proveedorId!=null){
//            strQuery.append("and p.proveedor_id = :proveedor_id ");
//        }
//        if(vendedorId!=null){
//            strQuery.append("and p.vendedor_id = :vendedor_i ");
//        }
//        if(formaPago!=null){
//            strQuery.append("and p.forma_pago = :forma_pago ");
//        }
//        if(productoId!=null){
//            strQuery.append("and pi2.producto_id = :producto_id ");
//        }
//        Query q = entityManager.createNamedQuery(strQuery.toString(), Pedido.class);
//        return q.getResultList();
//    }

    @Override
    public Pedido save(Pedido entity) {
        if(entity.getId()==null){
            entity.setCreadoEn(LocalDateTime.now());
            entity.setEstado(PedidoEstado.ABIERTO);
        }
        Pedido e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}