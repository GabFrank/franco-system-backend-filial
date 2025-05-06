ALTER TABLE operaciones.venta DROP CONSTRAINT fk_venta_delivery;
ALTER TABLE operaciones.venta ADD CONSTRAINT fk_venta_delivery FOREIGN KEY (delivery_id) REFERENCES operaciones.delivery(id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE operaciones.delivery DROP CONSTRAINT delivery_venta_id_fkey;
ALTER TABLE operaciones.delivery DROP COLUMN venta_id;
