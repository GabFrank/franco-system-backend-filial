-- Crear el tipo ENUM
CREATE TYPE financiero.estado_lote_de_enum AS ENUM (
    'PENDIENTE_ENVIO',
    'EN_PROCESO',
    'PROCESADO',
    'PROCESADO_CON_ERRORES',
    'ERROR_ENVIO',
    'ERROR_RED',
    'ERROR_PERMANENTE',
    'RECHAZADO'
);

-- Eliminar índices existentes antes de cambiar el tipo
DROP INDEX IF EXISTS financiero.idx_lote_de_estado;
DROP INDEX IF EXISTS financiero.idx_lote_de_error_red;

-- Cambiar el tipo de la columna
ALTER TABLE financiero.lote_de 
ALTER COLUMN estado TYPE financiero.estado_lote_de_enum 
USING estado::text::financiero.estado_lote_de_enum;

-- Recrear los índices con el nuevo tipo
CREATE INDEX idx_lote_de_estado ON financiero.lote_de(estado);
CREATE INDEX idx_lote_de_error_red ON financiero.lote_de(estado) WHERE estado = 'ERROR_RED';