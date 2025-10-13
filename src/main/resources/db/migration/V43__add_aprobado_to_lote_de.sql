-- Migración para agregar columna aprobado a lote_de
-- Fecha: 2025-10-13
-- Descripción: Agrega columna para marcar si un lote fue aprobado (anteriormente V999)

-- Agregar columna aprobado a lote_de (IF NOT EXISTS para evitar error si ya existe)
ALTER TABLE financiero.lote_de 
ADD COLUMN IF NOT EXISTS aprobado BOOLEAN DEFAULT FALSE;

-- Comentario
COMMENT ON COLUMN financiero.lote_de.aprobado IS 
    'Indica si el lote fue aprobado por SIFEN (todos los DEs del lote fueron aprobados)';

