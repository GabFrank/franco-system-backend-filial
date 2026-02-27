-- Migración para crear tabla de eventos de nominación de documentos electrónicos
-- Fecha: 2025-10-13
-- Descripción: Crea la tabla para almacenar eventos de nominación de receptores en DEs SIFEN

-- Crear la tabla evento_nominacion_de
CREATE TABLE financiero.evento_nominacion_de (
    id BIGSERIAL PRIMARY KEY,
    documento_electronico_id BIGINT NOT NULL,
    evento_id VARCHAR(50),
    fecha_firma TIMESTAMP,
    cdc_documento VARCHAR(50),
    
    -- Datos del receptor nominado
    cliente_id BIGINT,
    nombre_receptor VARCHAR(255),
    documento_receptor VARCHAR(50),
    tipo_receptor VARCHAR(50),
    
    -- Datos de la factura
    total_factura NUMERIC(15, 2),
    fecha_emision TIMESTAMP,
    fecha_recepcion TIMESTAMP,
    
    -- XML del evento
    xml_evento TEXT,
    
    -- Respuesta de SIFEN
    estado financiero.estado_evento_enum,
    fecha_procesamiento TIMESTAMP,
    protocolo_autorizacion VARCHAR(50),
    codigo_respuesta VARCHAR(10),
    mensaje_respuesta TEXT,
    respuesta_bruta TEXT,
    
    -- Auditoría
    activo BOOLEAN DEFAULT TRUE,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT,
    
    -- Constraints
    CONSTRAINT fk_evento_nominacion_documento_electronico 
        FOREIGN KEY (documento_electronico_id) 
        REFERENCES financiero.documento_electronico(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_evento_nominacion_cliente 
        FOREIGN KEY (cliente_id) 
        REFERENCES personas.cliente(id) 
        ON DELETE SET NULL,
    
    CONSTRAINT fk_evento_nominacion_usuario 
        FOREIGN KEY (usuario_id) 
        REFERENCES personas.usuario(id) 
        ON DELETE SET NULL
);

-- Crear índices para optimizar consultas
CREATE INDEX idx_evento_nominacion_documento_electronico_id 
    ON financiero.evento_nominacion_de(documento_electronico_id);

CREATE INDEX idx_evento_nominacion_evento_id 
    ON financiero.evento_nominacion_de(evento_id);

CREATE INDEX idx_evento_nominacion_cdc_documento 
    ON financiero.evento_nominacion_de(cdc_documento);

CREATE INDEX idx_evento_nominacion_estado 
    ON financiero.evento_nominacion_de(estado);

CREATE INDEX idx_evento_nominacion_cliente_id 
    ON financiero.evento_nominacion_de(cliente_id);

CREATE INDEX idx_evento_nominacion_fecha_firma 
    ON financiero.evento_nominacion_de(fecha_firma);

-- Índice compuesto para búsquedas de eventos activos por documento
CREATE INDEX idx_evento_nominacion_doc_activo_fecha 
    ON financiero.evento_nominacion_de(documento_electronico_id, activo, creado_en DESC);

-- Índice compuesto para búsquedas de eventos activos por CDC
CREATE INDEX idx_evento_nominacion_cdc_activo_fecha 
    ON financiero.evento_nominacion_de(cdc_documento, activo, creado_en DESC);

-- Agregar constraint único para evitar duplicados de eventos
CREATE UNIQUE INDEX idx_evento_nominacion_evento_id_unique 
    ON financiero.evento_nominacion_de(evento_id);

-- Agregar comentarios
COMMENT ON TABLE financiero.evento_nominacion_de IS 
    'Almacena eventos de nominación de receptores para documentos electrónicos innominados enviados a SIFEN';

COMMENT ON COLUMN financiero.evento_nominacion_de.evento_id IS 
    'ID único del evento generado al momento de enviar la nominación';

COMMENT ON COLUMN financiero.evento_nominacion_de.cdc_documento IS 
    'CDC del documento electrónico que se está nominando';

COMMENT ON COLUMN financiero.evento_nominacion_de.cliente_id IS 
    'Cliente nominado como receptor real de la factura';

COMMENT ON COLUMN financiero.evento_nominacion_de.estado IS 
    'Estado del evento: PENDIENTE (enviado), APROBADO (aceptado por SIFEN), RECHAZADO (rechazado), ERROR_ENVIO (error al enviar)';

COMMENT ON COLUMN financiero.evento_nominacion_de.protocolo_autorizacion IS 
    'Protocolo de autorización asignado por SIFEN cuando el evento es aprobado';

COMMENT ON COLUMN financiero.evento_nominacion_de.respuesta_bruta IS 
    'XML completo de respuesta de SIFEN para debugging y auditoría';

