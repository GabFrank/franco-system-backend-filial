-- Agregar columna producto_id a factura_legal_item para mantener vínculo con producto
-- cuando se crea una factura legal independiente (no vinculada a una venta)

ALTER TABLE financiero.factura_legal_item 
ADD COLUMN producto_id BIGINT;

-- Agregar foreign key constraint
ALTER TABLE financiero.factura_legal_item 
ADD CONSTRAINT fk_factura_legal_item_producto 
FOREIGN KEY (producto_id) REFERENCES productos.producto(id);

-- Agregar índice para mejorar performance
CREATE INDEX idx_factura_legal_item_producto_id 
ON financiero.factura_legal_item(producto_id);

-- Comentario en la columna
COMMENT ON COLUMN financiero.factura_legal_item.producto_id IS 'Referencia al producto para facturas legales independientes (no vinculadas a venta)';
