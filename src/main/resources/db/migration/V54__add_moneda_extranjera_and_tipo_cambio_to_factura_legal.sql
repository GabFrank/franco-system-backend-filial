-- Agregar campos moneda_extranjera y tipo_cambio a la tabla factura_legal
ALTER TABLE financiero.factura_legal 
ADD COLUMN moneda_extranjera VARCHAR(3),
ADD COLUMN tipo_cambio NUMERIC(10,4);

