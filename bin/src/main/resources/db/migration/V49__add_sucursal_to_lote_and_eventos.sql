-- Agregar columna sucursal_id a lote_de
ALTER TABLE financiero.lote_de 
ADD COLUMN sucursal_id BIGINT;

-- Agregar columna sucursal_id a evento_nominacion_de
ALTER TABLE financiero.evento_nominacion_de 
ADD COLUMN sucursal_id BIGINT;

-- Agregar columna sucursal_id a evento_cancelacion_de
ALTER TABLE financiero.evento_cancelacion_de 
ADD COLUMN sucursal_id BIGINT;

-- Agregar foreign keys
ALTER TABLE financiero.lote_de 
ADD CONSTRAINT fk_lote_de_sucursal 
FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id);

ALTER TABLE financiero.evento_nominacion_de 
ADD CONSTRAINT fk_evento_nominacion_de_sucursal 
FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id);

ALTER TABLE financiero.evento_cancelacion_de 
ADD CONSTRAINT fk_evento_cancelacion_de_sucursal 
FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id);

-- Crear índices para mejorar el rendimiento de las consultas
CREATE INDEX idx_lote_de_sucursal_id ON financiero.lote_de(sucursal_id);
CREATE INDEX idx_evento_nominacion_de_sucursal_id ON financiero.evento_nominacion_de(sucursal_id);
CREATE INDEX idx_evento_cancelacion_de_sucursal_id ON financiero.evento_cancelacion_de(sucursal_id);

-- Crear índices compuestos para la replicación (id, sucursal_id)
CREATE INDEX idx_lote_de_id_sucursal ON financiero.lote_de(id, sucursal_id);
CREATE INDEX idx_evento_nominacion_de_id_sucursal ON financiero.evento_nominacion_de(id, sucursal_id);
CREATE INDEX idx_evento_cancelacion_de_id_sucursal ON financiero.evento_cancelacion_de(id, sucursal_id);

