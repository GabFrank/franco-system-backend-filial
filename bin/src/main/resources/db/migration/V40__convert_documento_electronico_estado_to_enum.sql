-- Migración para convertir el campo estado de documento_electronico a enum PostgreSQL
-- Fecha: 2024-01-XX
-- Descripción: Convertir el campo estado de VARCHAR a enum para mejor consistencia y rendimiento

-- Fase 1: Crear el tipo enum para EstadoDE
CREATE TYPE financiero.estado_de_enum AS ENUM (
    'PENDIENTE',
    'EN_LOTE', 
    'APROBADO',
    'RECHAZADO',
    'CANCELADO'
);

-- Fase 2: Eliminar la columna VARCHAR existente
ALTER TABLE financiero.documento_electronico 
DROP COLUMN estado;

-- Fase 3: Crear la nueva columna con el tipo enum
ALTER TABLE financiero.documento_electronico 
ADD COLUMN estado financiero.estado_de_enum NOT NULL DEFAULT 'PENDIENTE';

-- Fase 4: Crear índice para optimizar consultas por estado
CREATE INDEX idx_documento_electronico_estado ON financiero.documento_electronico(estado);

-- Fase 5: Actualizar comentarios
COMMENT ON COLUMN financiero.documento_electronico.estado IS 'Estado del documento electrónico: PENDIENTE, EN_LOTE, APROBADO, RECHAZADO, CANCELADO';
COMMENT ON TYPE financiero.estado_de_enum IS 'Enum para los estados de documentos electrónicos SIFEN';
