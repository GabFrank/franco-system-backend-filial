-- Agregar campos unidad_medida e iva a la tabla factura_legal_item
ALTER TABLE financiero.factura_legal_item 
ADD COLUMN unidad_medida VARCHAR(50),
ADD COLUMN iva INTEGER;

