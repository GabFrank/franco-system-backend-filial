-- Migración para crear tabla de eventos de cancelación de documentos electrónicos
-- Fecha: 2025-10-10
-- Descripción: Crea la tabla para almacenar eventos de cancelación de DEs SIFEN

-- Fase 1: Crear el tipo enum para EstadoEvento
CREATE TYPE financiero.estado_evento_enum AS ENUM (
    'PENDIENTE',
    'APROBADO',
    'RECHAZADO',
    'ERROR_ENVIO'
);

-- Fase 2: Crear la tabla evento_cancelacion_de
CREATE TABLE financiero.evento_cancelacion_de (
    id BIGSERIAL PRIMARY KEY,
    documento_electronico_id BIGINT NOT NULL,
    evento_id VARCHAR(255) NOT NULL,
    fecha_firma TIMESTAMP NOT NULL,
    cdc_documento VARCHAR(44) NOT NULL,
    motivo_cancelacion TEXT,
    xml_evento TEXT,
    estado financiero.estado_evento_enum NOT NULL DEFAULT 'PENDIENTE',
    fecha_procesamiento TIMESTAMP,
    protocolo_autorizacion VARCHAR(100),
    codigo_respuesta VARCHAR(10),
    mensaje_respuesta TEXT,
    respuesta_bruta TEXT,
    activo BOOLEAN DEFAULT TRUE,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT,
    
    -- Constraints
    CONSTRAINT fk_evento_cancelacion_documento_electronico 
        FOREIGN KEY (documento_electronico_id) 
        REFERENCES financiero.documento_electronico(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_evento_cancelacion_usuario 
        FOREIGN KEY (usuario_id) 
        REFERENCES personas.usuario(id) 
        ON DELETE SET NULL
);

-- Fase 3: Crear índices para optimizar consultas
CREATE INDEX idx_evento_cancelacion_documento_electronico_id 
    ON financiero.evento_cancelacion_de(documento_electronico_id);

CREATE INDEX idx_evento_cancelacion_evento_id 
    ON financiero.evento_cancelacion_de(evento_id);

CREATE INDEX idx_evento_cancelacion_cdc_documento 
    ON financiero.evento_cancelacion_de(cdc_documento);

CREATE INDEX idx_evento_cancelacion_estado 
    ON financiero.evento_cancelacion_de(estado);

CREATE INDEX idx_evento_cancelacion_fecha_firma 
    ON financiero.evento_cancelacion_de(fecha_firma);

-- Índice compuesto para búsquedas de eventos activos por documento
CREATE INDEX idx_evento_cancelacion_doc_activo_fecha 
    ON financiero.evento_cancelacion_de(documento_electronico_id, activo, creado_en DESC);

-- Índice compuesto para búsquedas de eventos activos por CDC
CREATE INDEX idx_evento_cancelacion_cdc_activo_fecha 
    ON financiero.evento_cancelacion_de(cdc_documento, activo, creado_en DESC);

-- Fase 4: Agregar constraint único para evitar duplicados de eventos
CREATE UNIQUE INDEX idx_evento_cancelacion_evento_id_unique 
    ON financiero.evento_cancelacion_de(evento_id);

-- Fase 5: Agregar comentarios
COMMENT ON TABLE financiero.evento_cancelacion_de IS 
    'Almacena eventos de cancelación de documentos electrónicos enviados a SIFEN';

COMMENT ON COLUMN financiero.evento_cancelacion_de.evento_id IS 
    'ID único del evento generado al momento de enviar la cancelación';

COMMENT ON COLUMN financiero.evento_cancelacion_de.cdc_documento IS 
    'CDC del documento electrónico que se está cancelando';

COMMENT ON COLUMN financiero.evento_cancelacion_de.estado IS 
    'Estado del evento: PENDIENTE (enviado), APROBADO (aceptado por SIFEN), RECHAZADO (rechazado), ERROR_ENVIO (error al enviar)';

COMMENT ON COLUMN financiero.evento_cancelacion_de.protocolo_autorizacion IS 
    'Protocolo de autorización asignado por SIFEN cuando el evento es aprobado';

COMMENT ON COLUMN financiero.evento_cancelacion_de.respuesta_bruta IS 
    'XML completo de respuesta de SIFEN para debugging y auditoría';

COMMENT ON TYPE financiero.estado_evento_enum IS 
    'Enum para los estados de eventos SIFEN (cancelación, inutilización, nominación)';

