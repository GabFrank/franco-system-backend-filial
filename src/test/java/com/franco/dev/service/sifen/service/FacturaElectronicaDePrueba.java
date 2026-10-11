package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;

import java.time.LocalDateTime;

/** Factura electronica minima que el armado del DE acepta, sin guardar (sin id ni creadoEn). */
public final class FacturaElectronicaDePrueba {

    private FacturaElectronicaDePrueba() {
    }

    public static TimbradoDetalle timbradoDetalle() {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(2L);
        sucursal.setCodigoEstablecimientoFactura("001");

        Timbrado timbrado = new Timbrado();
        timbrado.setNumero("12345678");
        timbrado.setRuc("80012345-6");
        timbrado.setRazonSocial("EMPRESA SA");
        timbrado.setFechaInicio(LocalDateTime.of(2025, 1, 1, 0, 0));
        timbrado.setIsElectronico(true);
        timbrado.setEmail("facturacion@empresa.test");
        timbrado.setTipoSociedad("2");
        timbrado.setCodActividadEconomicaPrincipal("47111");
        timbrado.setDescActividadEconomicaPrincipal("COMERCIO");

        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setId(9L);
        detalle.setTimbrado(timbrado);
        detalle.setSucursal(sucursal);
        detalle.setPuntoExpedicion("001");
        detalle.setTelefono("021123456");
        detalle.setDireccion("CALLE 1");
        detalle.setDepartamento("CANINDEYU");
        detalle.setCodigoCiudad("1");
        detalle.setCiudad("SALTO DEL GUAIRA");
        detalle.setNumeroActual(5104L);
        return detalle;
    }

    public static FacturaLegal factura() {
        FacturaLegal factura = new FacturaLegal();
        factura.setTimbradoDetalle(timbradoDetalle());
        factura.setNumeroFactura(5105);
        factura.setFecha(LocalDateTime.of(2026, 10, 11, 10, 0, 0));
        factura.setTotalFinal(11000.0);
        factura.setDescuento(0.0);
        factura.setSucursalId(2L);
        return factura;
    }

    public static FacturaLegalItem item(String descripcion, float cantidad, double precioUnitario) {
        FacturaLegalItem item = new FacturaLegalItem();
        item.setDescripcion(descripcion);
        item.setCantidad(cantidad);
        item.setPrecioUnitario(precioUnitario);
        item.setTotal(precioUnitario * cantidad);
        item.setIva(10);
        return item;
    }
}
