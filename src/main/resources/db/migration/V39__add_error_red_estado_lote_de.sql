-- Migración para agregar el nuevo estado ERROR_RED al sistema de lotes SIFEN
-- Fecha: 2024-01-XX
-- Descripción: Agregar estado ERROR_RED para manejar errores de conectividad/red en lotes de documentos electrónicos

-- Nota: El estado se almacena como VARCHAR(255), no como enum PostgreSQL
-- Por lo tanto, no necesitamos modificar tipos de datos, solo documentar el nuevo estado

-- Actualizar cualquier lote existente que pueda beneficiarse del nuevo estado
-- (Opcional: si hay lotes en ERROR_ENVIO que sabemos que son por problemas de red)
-- UPDATE financiero.lote_de 
-- SET estado = 'ERROR_RED' 
-- WHERE estado = 'ERROR_ENVIO' 
--   AND (respuesta_sifen ILIKE '%connection%' 
--        OR respuesta_sifen ILIKE '%timeout%' 
--        OR respuesta_sifen ILIKE '%network%'
--        OR respuesta_sifen ILIKE '%unreachable%');

-- Crear índice para optimizar consultas por el nuevo estado
CREATE INDEX IF NOT EXISTS idx_lote_de_error_red ON financiero.lote_de(estado) WHERE estado = 'ERROR_RED';

-- Actualizar comentarios para documentar el nuevo estado
COMMENT ON TABLE financiero.lote_de IS 'Tabla para gestionar lotes de documentos electrónicos SIFEN. Estados: PENDIENTE_ENVIO, EN_PROCESO, PROCESADO, PROCESADO_CON_ERRORES, ERROR_ENVIO, ERROR_RED (nuevo), ERROR_PERMANENTE, RECHAZADO';

COMMENT ON COLUMN financiero.lote_de.estado IS 'Estado del lote: PENDIENTE_ENVIO, EN_PROCESO, PROCESADO, PROCESADO_CON_ERRORES, ERROR_ENVIO, ERROR_RED (problemas de conectividad), ERROR_PERMANENTE, RECHAZADO';

-- Agregar comentario específico para el nuevo estado
-- El estado ERROR_RED indica problemas de conectividad/red que requieren espera antes del reintento
-- Diferencia de ERROR_ENVIO que usa backoff exponencial para errores de SIFEN
