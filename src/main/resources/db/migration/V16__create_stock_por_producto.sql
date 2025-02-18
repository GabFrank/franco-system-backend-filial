CREATE table if not exists operaciones.stock_por_producto_sucursal (
	producto_id int8 NOT NULL,
	last_movimiento_stock_id int8 not null,
	sucursal_id int8 NOT NULL,
	cantidad numeric DEFAULT 0 NOT null,
	CONSTRAINT stock_por_producto_sucursal_pk PRIMARY KEY (producto_id, sucursal_id),
	CONSTRAINT stock_por_producto_sucursal_sucursal_id_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) on delete cascade,
	CONSTRAINT stock_por_producto_sucursal_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id) on delete cascade
);