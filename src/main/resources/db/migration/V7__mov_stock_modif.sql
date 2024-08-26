ALTER TABLE operaciones.movimiento_stock ADD CONSTRAINT movimiento_stock_unique UNIQUE ("tipo_movimiento",referencia,sucursal_id);
