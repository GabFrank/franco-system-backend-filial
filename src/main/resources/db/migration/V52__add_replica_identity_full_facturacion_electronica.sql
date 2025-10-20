-- Agregar REPLICA IDENTITY FULL a tablas de facturación electrónica
-- Necesario para replicación lógica con identificador compuesto (id, sucursal_id)

ALTER TABLE financiero.documento_electronico REPLICA IDENTITY FULL;

ALTER TABLE financiero.lote_de REPLICA IDENTITY FULL;

ALTER TABLE financiero.evento_nominacion_de REPLICA IDENTITY FULL;

ALTER TABLE financiero.evento_cancelacion_de REPLICA IDENTITY FULL;

ALTER TABLE financiero.timbrado_detalle REPLICA IDENTITY FULL;
