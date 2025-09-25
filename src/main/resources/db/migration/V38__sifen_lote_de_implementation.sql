-- Migración para implementar el sistema de lotes de documentos electrónicos SIFEN
-- Fecha: 2024-01-XX
-- Descripción: Crear tabla lote_de y modificar documento_electronico para soportar el nuevo sistema asíncrono

-- Fase 1: Crear la nueva tabla para lotes de documentos electrónicos
CREATE TABLE financiero.lote_de (
    id BIGSERIAL PRIMARY KEY,
    estado VARCHAR(255) NOT NULL,
    fecha_procesado TIMESTAMP,
    fecha_ultimo_intento TIMESTAMP,
    intentos INTEGER DEFAULT 0,
    respuesta_sifen TEXT,
    protocolo VARCHAR(255),
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT,
    CONSTRAINT fk_lote_de_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

-- Fase 2: Modificar la tabla existente de documentos electrónicos
-- Añadir la columna para la clave foránea a lote_de
ALTER TABLE financiero.documento_electronico
ADD COLUMN lote_de_id BIGINT,
ADD CONSTRAINT fk_documento_electronico_lote_de FOREIGN KEY (lote_de_id) REFERENCES financiero.lote_de(id);

-- Renombrar la columna de estado y prepararla para el enum
ALTER TABLE financiero.documento_electronico
RENAME COLUMN estado_documento_electronico TO estado;

-- Fase 3: Migrar datos existentes
-- Actualizar documentos existentes que tengan estado 'PROCESADO' a 'APROBADO'
UPDATE financiero.documento_electronico 
SET estado = 'APROBADO' 
WHERE estado = 'PROCESADO';

-- Actualizar documentos existentes que tengan estado 'PENDIENTE' a 'PENDIENTE'
UPDATE financiero.documento_electronico 
SET estado = 'PENDIENTE' 
WHERE estado = 'PENDIENTE';

-- Actualizar documentos existentes que tengan estado 'RECHAZADO' a 'RECHAZADO'
UPDATE financiero.documento_electronico 
SET estado = 'RECHAZADO' 
WHERE estado = 'RECHAZADO';

-- Fase 4: Crear índices para optimizar consultas
CREATE INDEX idx_lote_de_estado ON financiero.lote_de(estado);
CREATE INDEX idx_lote_de_fecha_creacion ON financiero.lote_de(creado_en);
CREATE INDEX idx_documento_electronico_lote_de_id ON financiero.documento_electronico(lote_de_id);

-- Fase 5: Comentarios para documentación
COMMENT ON TABLE financiero.lote_de IS 'Tabla para gestionar lotes de documentos electrónicos enviados a SIFEN';
COMMENT ON COLUMN financiero.lote_de.estado IS 'Estado del lote: PENDIENTE_ENVIO, EN_PROCESO, PROCESADO, etc.';
COMMENT ON COLUMN financiero.lote_de.intentos IS 'Número de intentos de envío del lote';
COMMENT ON COLUMN financiero.lote_de.respuesta_sifen IS 'Respuesta completa de SIFEN para el lote';
COMMENT ON COLUMN financiero.lote_de.protocolo IS 'Número de protocolo asignado por SIFEN';

COMMENT ON COLUMN financiero.documento_electronico.lote_de_id IS 'Referencia al lote al que pertenece este documento';
COMMENT ON COLUMN financiero.documento_electronico.estado IS 'Estado del documento: PENDIENTE, EN_LOTE, APROBADO, RECHAZADO, CANCELADO';
