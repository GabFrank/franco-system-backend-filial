ALTER TABLE operaciones.transferencia_item DROP CONSTRAINT transferencia_item_transferencia_fk;
ALTER TABLE operaciones.transferencia_item ADD CONSTRAINT transferencia_item_transferencia_fk FOREIGN KEY (transferencia_id) REFERENCES operaciones.transferencia(id) ON DELETE CASCADE ON UPDATE CASCADE;
