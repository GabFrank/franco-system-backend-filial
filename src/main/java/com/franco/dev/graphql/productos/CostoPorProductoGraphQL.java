package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.graphql.productos.input.CostoPorProductoInput;
import com.franco.dev.service.productos.CostosPorSucursalService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CostoPorProductoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CostosPorSucursalService service;

//    public CostosPorSucursal costoPorProductoLastPorProductoId(Long proId, Long sucId) {return service.findLastByProductoIdAndSucursalId(proId, sucId);}
//    public List<CostoPorProducto> costosPorPorProductoId(Long id) {return service.findLastByProductoId(id);}

    public CostoPorProducto saveCostoPorProducto(CostoPorProductoInput input){
        if(input.getId()==null) input.setCreadoEn(LocalDateTime.now());
        ModelMapper m = new ModelMapper();
        CostoPorProducto e = m.map(input, CostoPorProducto.class);
        return service.save(e);
    }

    public Boolean deleteCostoPorProducto(Long id){
        return service.deleteById(id);
    }

}
