ALTER TABLE operaciones.venta ADD CONSTRAINT venta_unique UNIQUE (delivery_id,sucursal_id);
