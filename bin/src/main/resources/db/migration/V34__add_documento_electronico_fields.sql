-- NOTA: Los campos de documento electrónico se almacenan en la tabla documento_electronico
-- No se agregan campos duplicados a factura_legal para evitar redundancia

-- Crear tabla documento_electronico
CREATE TABLE financiero.documento_electronico (
    id BIGSERIAL PRIMARY KEY,
    sucursal_id BIGINT NOT NULL,
    factura_legal_id BIGINT NOT NULL REFERENCES financiero.factura_legal(id),
    cdc VARCHAR(50),
    url_qr VARCHAR(500),
    xml_firmado TEXT,
    xml_original TEXT,
    estado_documento_electronico VARCHAR(50),
    codigo_respuesta_sifen VARCHAR(10),
    mensaje_respuesta_sifen VARCHAR(500),
    numero_documento VARCHAR(20),
    tipo_documento VARCHAR(20),
    fecha_emision TIMESTAMP,
    fecha_recepcion_sifen TIMESTAMP,
    activo BOOLEAN DEFAULT TRUE,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT REFERENCES personas.usuario(id)
);

-- Crear índices para mejorar el rendimiento
CREATE INDEX idx_documento_electronico_factura_legal_id ON financiero.documento_electronico(factura_legal_id);
CREATE INDEX idx_documento_electronico_cdc ON financiero.documento_electronico(cdc);
CREATE INDEX idx_documento_electronico_estado ON financiero.documento_electronico(estado_documento_electronico);
CREATE INDEX idx_documento_electronico_sucursal_id ON financiero.documento_electronico(sucursal_id);
CREATE INDEX idx_documento_electronico_fecha_emision ON financiero.documento_electronico(fecha_emision);

-- Agregar comentarios a las columnas
COMMENT ON TABLE financiero.documento_electronico IS 'Tabla para almacenar documentos electrónicos generados con SIFEN';
COMMENT ON COLUMN financiero.documento_electronico.cdc IS 'Código de Control del documento electrónico';
COMMENT ON COLUMN financiero.documento_electronico.url_qr IS 'URL del código QR para consulta del documento';
COMMENT ON COLUMN financiero.documento_electronico.xml_firmado IS 'XML firmado del documento electrónico';
COMMENT ON COLUMN financiero.documento_electronico.xml_original IS 'XML original antes de la firma';
COMMENT ON COLUMN financiero.documento_electronico.estado_documento_electronico IS 'Estado del documento en SIFEN (RECIBIDO, PROCESADO, RECHAZADO, etc.)';
COMMENT ON COLUMN financiero.documento_electronico.codigo_respuesta_sifen IS 'Código de respuesta de SIFEN';
COMMENT ON COLUMN financiero.documento_electronico.mensaje_respuesta_sifen IS 'Mensaje de respuesta de SIFEN';
