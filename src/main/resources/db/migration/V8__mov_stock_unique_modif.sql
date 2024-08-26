ALTER TABLE operaciones.movimiento_stock DROP CONSTRAINT movimiento_stock_unique;
ALTER TABLE operaciones.movimiento_stock ADD CONSTRAINT movimiento_stock_unique UNIQUE (producto_id,"tipo_movimiento",referencia,sucursal_id);
