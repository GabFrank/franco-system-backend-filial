package com.franco.dev.service.financiero.builder;

/**
 * Descriptor neutro al canal de un item de factura legal.
 * Cualquier caller (GraphQL mutation, REST API, FacturaService desde venta,
 * delivery, etc.) construye una lista de estos y se la pasa al
 * FacturaLegalBuilder. El builder se encarga de resolver IDs a entidades,
 * resolver iva, calcular parciales y persistir.
 *
 * Reglas:
 *  - iva puede ser null. El IvaResolver decide.
 *  - productoId, ventaItemId, presentacionId opcionales (al menos uno suele venir).
 *  - descripcion siempre requerida (string libre o copiada del producto).
 *  - total puede venir pre-calculado o calculado por cantidad*precioUnitario.
 *  - unidadMedida default "UNIDAD" si null.
 */
public class FacturaLegalItemDescriptor {

    private Integer iva;
    private Long productoId;
    private Long ventaItemId;
    private Long presentacionId;
    private String descripcion;
    private Double precioUnitario;
    private Double total;
    private Float cantidad;
    private String unidadMedida;
    private Long usuarioId;

    public Integer getIva() { return iva; }
    public void setIva(Integer iva) { this.iva = iva; }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

    public Long getVentaItemId() { return ventaItemId; }
    public void setVentaItemId(Long ventaItemId) { this.ventaItemId = ventaItemId; }

    public Long getPresentacionId() { return presentacionId; }
    public void setPresentacionId(Long presentacionId) { this.presentacionId = presentacionId; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Double getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(Double precioUnitario) { this.precioUnitario = precioUnitario; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public Float getCantidad() { return cantidad; }
    public void setCantidad(Float cantidad) { this.cantidad = cantidad; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
}
