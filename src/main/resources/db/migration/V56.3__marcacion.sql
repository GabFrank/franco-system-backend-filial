ALTER TABLE administrativo.marcacion 
    RENAME COLUMN sucursal_id TO sucursal_entrada_id;

ALTER TABLE administrativo.marcacion 
    RENAME COLUMN creado_en TO fecha_entrada;

ALTER TABLE administrativo.marcacion 
    ADD COLUMN sucursal_salida_id BIGINT,
    ADD COLUMN fecha_salida TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE administrativo.marcacion
    ADD CONSTRAINT fk_marcacion_sucursal_salida 
    FOREIGN KEY (sucursal_salida_id) 
    REFERENCES empresarial.sucursal (id);

ALTER TABLE administrativo.marcacion
    ADD COLUMN latitud NUMERIC(10, 7),
    ADD COLUMN longitud NUMERIC(10, 7),
    ADD COLUMN precision_gps REAL,
    ADD COLUMN distancia_sucursal INTEGER,
    ADD COLUMN device_id VARCHAR(255),
    ADD COLUMN device_info VARCHAR(255);

ALTER SEQUENCE administrativo.marcacion_id_seq INCREMENT BY 2;
SELECT setval('administrativo.marcacion_id_seq', (SELECT COALESCE(MAX(id), 0) + (CASE WHEN (COALESCE(MAX(id), 0) % 2) = 0 THEN 2 ELSE 1 END) FROM administrativo.marcacion), false);