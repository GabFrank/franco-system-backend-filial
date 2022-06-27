package com.franco.dev.graphql.productos;

import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.productos.CostosPorSucursalService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductoExistenciaCostoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProductoService service;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private CostosPorSucursalService costosPorSucursalService;

//    public List<ProductoExistenciaCosto> productoExistenciaCostosPorProveedor(Long proId, String text) {
//        List<ProductoExistenciaCosto> productoExistenciaCostoList = new ArrayList<>();
//        List<Producto> productosList;
//        if(proId!=null){
//            productosList = service.findByProveedorId(proId, text);
//        } else {
//            productosList = service.findAll2();
//        }
//        List<Sucursal> sucList = sucursalService.findAll2();
//        for (Producto p : productosList) {
//            List<ExistenciaPorSucursal> epsList = new ArrayList<>();
//            System.out.println(p.toString());
//            ProductoExistenciaCosto p2 = new ProductoExistenciaCosto();
//            //adicionar producto
//            p2.setProducto(p);
//            //recorrer sucursales y adicionar sucursales y existencias
//            for (Sucursal s: sucList){
//                ExistenciaPorSucursal eps = new ExistenciaPorSucursal();
//                eps.setSucursal(s);
//                Float existencia = movimientoStockService.stockByProductoIdAndSucursalId(p.getId(), s.getId());
//                Float upc;
//                Float cpc;
//                if(existencia==null){
//                    existencia = Float.parseFloat("0");
//                }
//                eps.setExistencia(existencia);
//                epsList.add(eps);
//            }
//            //adicionar existencia por sucursal a producto exististencia costo
//            p2.setExistenciaPorSucursalList(epsList);
//
//            //adicionar producto existencia costo a la lista
//            productoExistenciaCostoList.add(p2);
//        }
//        return productoExistenciaCostoList;
//    }

//    public ProductoExistenciaCosto productoExistenciaCostos(Producto p) {
//            List<Sucursal> sucList = sucursalService.findAll2();
//            List<ExistenciaPorSucursal> epsList = new ArrayList<>();
//            ProductoExistenciaCosto p2 = new ProductoExistenciaCosto();
//            //adicionar producto
//            p2.setProducto(p);
//            //recorrer sucursales y adicionar sucursales y existencias
//            for (Sucursal s: sucList){
//                ExistenciaPorSucursal eps = new ExistenciaPorSucursal();
//                eps.setSucursal(s);
//                Float existencia = movimientoStockService.stockByProductoIdAndSucursalId(p.getId(), s.getId());
//                Float upc;
//                Float cpc;
//                if(existencia==null){
//                    existencia = Float.parseFloat("0");
//                }
//                eps.setExistencia(existencia);
//                epsList.add(eps);
//            }
//            //adicionar existencia por sucursal a producto exististencia costo
//            p2.setExistenciaPorSucursalList(epsList);
//
//            //adicionar producto existencia costo a la lista
//        return p2;
//    }


}
