package com.franco.dev.repository.productos;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.HelperRepository;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductoRepository extends HelperRepository<Producto, Long> {

    default Class<Producto> getEntityClass() {
        return Producto.class;
    }

    public Producto findByDescripcion(String texto);

    public Producto findByIdCentral(Long id);

    @Query(value = "select distinct on (p.id, p.descripcion) p.id, p.descripcion, p.balanza , p.cambiable , p.combo , p.creado_en , p.descripcion_factura , p.dias_vencimiento, p.es_alcoholico , p.garantia , p.id_central , p.id_sucursal_origen , p.imagenes , p.ingrediente , p.iva, p.observacion , p.promocion , p.propagado , p.stock , p.sub_familia_id , p.tiempo_garantia , p.tipo_conservacion , p.unidad_por_caja , p.unidad_por_caja_secundaria , p.usuario_id , p.vencimiento, p.is_envase, p.envase_id  \n" +
            "from productos.producto p \n" +
            "left outer join productos.presentacion p2 on p2.producto_id = p.id \n" +
            "left outer join productos.codigo c on c.presentacion_id = p2.id \n" +
            "where CAST(p.id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.descripcion_factura) like %?1% or c.codigo like %?1% " +
            "ORDER BY p.descripcion asc \n" +
            "limit 10 " +
            "offset ?2", nativeQuery = true)
    public List<Producto> findbyAll(String texto, int offset);

    @Query(value = "select distinct on (p.id, p.descripcion) p.id, p.descripcion, p.balanza , p.cambiable , p.combo , p.creado_en , p.descripcion_factura , p.dias_vencimiento, p.es_alcoholico , p.garantia , p.id_central , p.id_sucursal_origen , p.imagenes , p.ingrediente , p.iva, p.observacion , p.promocion , p.propagado , p.stock , p.sub_familia_id , p.tiempo_garantia , p.tipo_conservacion , p.unidad_por_caja , p.unidad_por_caja_secundaria , p.usuario_id , p.vencimiento,p.is_envase, p.envase_id  \n" +
            "from productos.producto p \n" +
            "left outer join productos.presentacion p2 on p2.producto_id = p.id \n" +
            "left outer join productos.codigo c on c.presentacion_id = p2.id \n" +
            "where p.is_envase = true and CAST(p.id as text) like %?1% or UPPER(p.descripcion) like %?1% " +
            "ORDER BY p.descripcion asc \n" +
            "limit 10 " +
            "offset ?2", nativeQuery = true)
    public List<Producto> findEnvases(String texto, int offset, Boolean isEnvase);

    public List<Producto> findBySubfamiliaId(Long id);

    @Query("select distinct pro FROM ProductoProveedor ci " +
            "left outer join ci.proveedor as p " +
            "left outer join ci.producto as pro " +
            "where p.id = ?1 AND (CAST(pro.id as text) like %?2% or UPPER(pro.descripcion) like %?2% or UPPER(pro.descripcionFactura) like %?2%)")
    public List<Producto> findByProveedorId(Long id, String text);

    @Query(value = "select * from productos.producto p " +
            "left join productos.presentacion p2 on p2.producto_id = p.id " +
            "left join productos.codigo c on c.presentacion_id = p2.id " +
            "where c.codigo = ?1", nativeQuery = true)
    public Producto findByCodigo(String texto);

    @Query(value="select * from productos.producto p \n" +
            "where p.activo = true", nativeQuery = true)
    public List<Producto> findAllForPdv();

    @Query(value = "select * from productos.producto p " +
            "join productos.pdv_grupos_productos pgp on pgp.producto_id = p.id " +
            "where pgp.id = ?1", nativeQuery = true)
    public List<Producto> findByPdvGrupoProductoId(Long id);

}
