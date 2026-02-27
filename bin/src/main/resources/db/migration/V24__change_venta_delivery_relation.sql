ALTER TABLE operaciones.venta
ADD COLUMN delivery_id int8;

UPDATE operaciones.venta
SET delivery_id = delivery.id
FROM operaciones.delivery
WHERE delivery.venta_id = venta.id and delivery.sucursal_id = venta.sucursal_id;

ALTER TABLE operaciones.venta
ADD CONSTRAINT fk_venta_delivery
FOREIGN KEY (delivery_id)
REFERENCES operaciones.delivery(id);

