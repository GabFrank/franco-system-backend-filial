-- Tabla impresora en la filial (lado SUBSCRIBER de la replicacion logica).
-- El registro se administra en el servidor central y se replica hacia la filial via
-- central_pub. Aca solo se crea la estructura para recibir las filas replicadas:
--   * id INTEGER (NO serial): el id lo genera el central, no la filial.
--   * sin FKs: convencion de tablas replicadas en la filial (evita fallos de apply si
--     el orden de replicacion de sucursal/usuario no esta garantizado).
--   * sin ALTER PUBLICATION: impresora es unidireccional central -> filial.
-- Los tipos de columna coinciden con la tabla del central (V142.3) para que la
-- replicacion aplique sin conflictos.

CREATE TABLE IF NOT EXISTS empresarial.impresora (
    id INTEGER PRIMARY KEY,
    nombre VARCHAR(120),
    activo BOOLEAN DEFAULT TRUE,
    es_predeterminada BOOLEAN DEFAULT FALSE,
    sucursal_id BIGINT,
    tipo VARCHAR(20),
    uso VARCHAR(20),
    conexion VARCHAR(20),
    cola_cups VARCHAR(255),
    ip VARCHAR(60),
    puerto INTEGER,
    perfil_papel VARCHAR(20),
    columnas INTEGER,
    ancho_mm INTEGER,
    alto_mm INTEGER,
    marca VARCHAR(60),
    codepage VARCHAR(40),
    creado_en TIMESTAMP,
    usuario_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_impresora_sucursal_id ON empresarial.impresora(sucursal_id);
