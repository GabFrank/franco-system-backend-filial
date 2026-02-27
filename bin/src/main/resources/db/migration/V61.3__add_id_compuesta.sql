-- Limpiar datos de prueba para poder aplicar constraints limpiamente
TRUNCATE TABLE administrativo.jornada CASCADE;
TRUNCATE TABLE administrativo.marcacion CASCADE;

-- Eliminar constraints y secuencias actuales
ALTER TABLE administrativo.jornada DROP CONSTRAINT IF EXISTS pk_jornada CASCADE;
ALTER TABLE administrativo.marcacion DROP CONSTRAINT IF EXISTS uq_marcacion_id CASCADE;
ALTER TABLE administrativo.marcacion DROP CONSTRAINT IF EXISTS marcacion_pk CASCADE;
ALTER TABLE administrativo.marcacion DROP CONSTRAINT IF EXISTS marcacion_pkey CASCADE;

DROP SEQUENCE IF EXISTS administrativo.jornada_id_seq CASCADE;
DROP SEQUENCE IF EXISTS administrativo.marcacion_id_seq CASCADE;

-- Agregar sucursal_id a marcacion
ALTER TABLE administrativo.marcacion 
    ADD COLUMN sucursal_id BIGINT NOT NULL,
    ADD CONSTRAINT pk_marcacion PRIMARY KEY (id, sucursal_id);

-- Agregar sucursal_id a jornada
ALTER TABLE administrativo.jornada 
    ADD COLUMN sucursal_id BIGINT NOT NULL,
    ADD COLUMN entrada_sucursal_id BIGINT,
    ADD COLUMN salida_sucursal_id BIGINT,
    ADD COLUMN marcacion_salida_almuerzo_suc_id BIGINT,
    ADD COLUMN marcacion_entrada_almuerzo_suc_id BIGINT,
    ADD CONSTRAINT pk_jornada PRIMARY KEY (id, sucursal_id);

-- Recrear Foreign Keys en jornada hacia marcacion usando la clave compuesta
ALTER TABLE administrativo.jornada
    ADD CONSTRAINT fk_jornada_entrada 
        FOREIGN KEY (entrada_id, entrada_sucursal_id) 
        REFERENCES administrativo.marcacion (id, sucursal_id),
    ADD CONSTRAINT fk_jornada_salida 
        FOREIGN KEY (salida_id, salida_sucursal_id) 
        REFERENCES administrativo.marcacion (id, sucursal_id),
    ADD CONSTRAINT fk_jornada_salida_almuerzo
        FOREIGN KEY (marcacion_salida_almuerzo_id, marcacion_salida_almuerzo_suc_id) 
        REFERENCES administrativo.marcacion (id, sucursal_id),
    ADD CONSTRAINT fk_jornada_entrada_almuerzo
        FOREIGN KEY (marcacion_entrada_almuerzo_id, marcacion_entrada_almuerzo_suc_id) 
        REFERENCES administrativo.marcacion (id, sucursal_id);

-- Recrear Index en jornada
DROP INDEX IF EXISTS administrativo.idx_jornada_usuario CASCADE;
DROP INDEX IF EXISTS administrativo.idx_jornada_fecha CASCADE;
CREATE INDEX idx_jornada_usuario ON administrativo.jornada(usuario_id);
CREATE INDEX idx_jornada_fecha ON administrativo.jornada(fecha);
