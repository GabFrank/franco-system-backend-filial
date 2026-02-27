ALTER TABLE operaciones.cobro_detalle ADD CONSTRAINT cobro_detalle_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL ON UPDATE CASCADE;
